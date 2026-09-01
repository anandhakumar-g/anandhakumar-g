package com.singlepoint.storage;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Stores attachments on the local filesystem; served back via {@code GET /api/v1/files/**}. */
@Service
@ConditionalOnProperty(name = "sp.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFsStorageService implements StorageService {

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalFsStorageService(@Value("${sp.storage.local.base-dir:./local-attachments}") String baseDir,
                                 @Value("${sp.storage.local.public-base-url:http://localhost:8080/api/v1/files}") String publicBaseUrl) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @PostConstruct
    void ensureDir() throws IOException {
        Files.createDirectories(baseDir);
    }

    @Override
    public String put(String keyPrefix, String filename, String contentType, byte[] content) {
        String safePrefix = keyPrefix == null ? "misc" : keyPrefix.replaceAll("[^a-zA-Z0-9/_-]", "");
        String ext = "";
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.')).replaceAll("[^a-zA-Z0-9.]", "");
        }
        String key = safePrefix + "/" + UUID.randomUUID() + ext;
        try {
            Path target = baseDir.resolve(key).normalize();
            if (!target.startsWith(baseDir)) throw new AppException(ErrorCode.STORAGE_ERROR, "bad key");
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            Files.writeString(Paths.get(target + ".type"), contentType == null ? "application/octet-stream" : contentType);
            return key;
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, "Could not store file", e);
        }
    }

    @Override
    public StoredObject get(String storageKey) {
        try {
            Path target = baseDir.resolve(storageKey).normalize();
            if (!target.startsWith(baseDir) || !Files.exists(target)) {
                throw AppException.notFound("File");
            }
            String type = "application/octet-stream";
            Path typeFile = Paths.get(target + ".type");
            if (Files.exists(typeFile)) type = Files.readString(typeFile).trim();
            byte[] bytes = Files.readAllBytes(target);
            return new StoredObject(new ByteArrayInputStream(bytes), type, bytes.length,
                    target.getFileName().toString());
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, "Could not read file", e);
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        return publicBaseUrl + "/" + storageKey;
    }
}
