package com.singlepoint.storage;

import java.io.InputStream;

/** Object storage abstraction. Local filesystem in dev/test, S3 in cloud (see ADR-008). */
public interface StorageService {

    /** Persist bytes and return the opaque storage key. */
    String put(String keyPrefix, String filename, String contentType, byte[] content);

    StoredObject get(String storageKey);

    /** A URL the client can use to fetch the object (app endpoint for local, presigned S3 URL for cloud). */
    String publicUrl(String storageKey);

    record StoredObject(InputStream content, String contentType, long size, String filename) { }
}
