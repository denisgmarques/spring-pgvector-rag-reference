package com.helpdesk.rag.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published by {@code UpdateDocumentUseCase} after the replacement-file transaction
 * commits, triggering post-commit deletion of old chunks and reprocessing (RF-06).
 */
public record DocumentUpdatedEvent(UUID documentId, Instant occurredAt) {

    public DocumentUpdatedEvent {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
