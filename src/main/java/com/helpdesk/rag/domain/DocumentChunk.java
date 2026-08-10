package com.helpdesk.rag.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain entity — zero framework dependencies (RNF-01).
 */
public class DocumentChunk {

    private final UUID id;
    private final UUID documentId;
    private final int chunkIndex;
    private final String chunkText;
    private final float[] embedding;
    private final Instant createdAt;

    public DocumentChunk(UUID id, UUID documentId, int chunkIndex, String chunkText, float[] embedding, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.chunkIndex = chunkIndex;
        this.chunkText = Objects.requireNonNull(chunkText, "chunkText must not be null");
        this.embedding = Objects.requireNonNull(embedding, "embedding must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
