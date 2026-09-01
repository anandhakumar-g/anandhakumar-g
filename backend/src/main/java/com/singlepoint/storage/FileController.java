package com.singlepoint.storage;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves locally-stored attachments. Cloud uses presigned S3 URLs instead of this endpoint. */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final StorageService storageService;

    public FileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/{*key}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String key) {
        String storageKey = key.startsWith("/") ? key.substring(1) : key;
        StorageService.StoredObject obj = storageService.get(storageKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + obj.filename() + "\"")
                .body(new InputStreamResource(obj.content()));
    }
}
