package com.helpdesk.rag.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain entity — zero framework dependencies (RNF-01). Enforces the document
 * state machine: PENDING -> PROCESSING -> PROCESSED | ERROR, with PROCESSED/ERROR
 * re-enterable into PROCESSING (update/reprocessing, RF-06), and any non-PROCESSING,
 * non-DELETED state able to transition into DELETED (soft delete, RF-08).
 */
public class Document {

    private final UUID id;
    private String fileName;
    private String contentType;
    private long fileSize;
    private byte[] fileData;
    private DocumentStatus status;
    private String errorMessage;
    private final Instant uploadedAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private long version;

    public Document(UUID id,
                     String fileName,
                     String contentType,
                     long fileSize,
                     byte[] fileData,
                     DocumentStatus status,
                     String errorMessage,
                     Instant uploadedAt,
                     Instant updatedAt,
                     Instant deletedAt,
                     long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileData = fileData;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errorMessage = errorMessage;
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public static Document uploadNew(UUID id, String fileName, String contentType, long fileSize, byte[] fileData, Instant uploadedAt) {
        return new Document(id, fileName, contentType, fileSize, fileData, DocumentStatus.PENDING, null, uploadedAt, null, null, 0L);
    }

    public void markProcessing() {
        if (status != DocumentStatus.PENDING && status != DocumentStatus.PROCESSED && status != DocumentStatus.ERROR) {
            throw new IllegalStateException("Cannot transition from " + status + " to PROCESSING");
        }
        this.status = DocumentStatus.PROCESSING;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markProcessed() {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot transition from " + status + " to PROCESSED");
        }
        this.status = DocumentStatus.PROCESSED;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markError(String message) {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Cannot transition from " + status + " to ERROR");
        }
        this.status = DocumentStatus.ERROR;
        this.errorMessage = Objects.requireNonNull(message, "message must not be null");
        this.updatedAt = Instant.now();
    }

    public void markDeleted(Instant now) {
        if (status == DocumentStatus.PROCESSING || status == DocumentStatus.DELETED) {
            throw new IllegalStateException("Cannot transition from " + status + " to DELETED");
        }
        this.status = DocumentStatus.DELETED;
        this.deletedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void replaceFile(String fileName, String contentType, long fileSize, byte[] fileData) {
        this.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileData = fileData;
    }

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
