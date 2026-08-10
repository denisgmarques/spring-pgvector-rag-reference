package com.helpdesk.rag.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published by {@code UploadDocumentUseCase} after the PENDING metadata transaction
 * commits, triggering post-commit extraction/chunking/embedding (RF-01).
 */
public record DocumentUploadedEvent(UUID documentId, Instant occurredAt) {

    public DocumentUploadedEvent {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
