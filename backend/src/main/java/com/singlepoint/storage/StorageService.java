package com.singlepoint.storage;

import java.io.InputStream;

/** Object storage abstraction. Local filesystem in dev/test, S3 in cloud (see ADR-008). */
public interface StorageService {

    /** Persist bytes and return the opaque storage key. */
    String put(String keyPrefix, String filename, String contentType, byte[] content);

    StoredObject get(String storageKey);

    /** A URL the client can use to fetch the object (app endpoint for local, presigned S3 URL for cloud). */
    String publicUrl(String storageKey);

    /**
     * Store a sensitive object (KYC docs). No public URL is ever produced — callers must stream
     * it back through an auth-gated endpoint. Local dev keeps it under a {@code private/} prefix;
     * cloud routes it to the separate KYC bucket.
     */
    default String putPrivate(String keyPrefix, String filename, String contentType, byte[] content) {
        return put("private/" + keyPrefix, filename, contentType, content);
    }

    default StoredObject getPrivate(String storageKey) {
        return get(storageKey);
    }

    record StoredObject(InputStream content, String contentType, long size, String filename) { }
}
