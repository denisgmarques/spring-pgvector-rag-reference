package com.helpdesk.rag.application.ports.out;

import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepositoryPort {

    Document save(Document document);

    Optional<Document> findById(UUID id);

    DocumentBatch findBatch(String cursor, int size);

    void deleteChunksByDocumentId(UUID documentId);

    void saveChunks(List<DocumentChunk> chunks);

    List<SimilarChunk> findSimilarChunks(float[] queryVector, int limit);

    record DocumentBatch(List<Document> items, String nextCursor) {
    }

    record SimilarChunk(UUID documentId, String documentName, String chunkText, double score) {
    }
}
