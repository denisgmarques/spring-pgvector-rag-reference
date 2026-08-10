package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataDocumentChunkJpaRepository extends JpaRepository<DocumentChunkJpaEntity, UUID> {

    void deleteByDocumentId(UUID documentId);

    /**
     * Row shape: {@code [document_id (UUID), document_name (String), chunk_text (String), score (BigDecimal)]}.
     * Score formula per RF-10: cosine distance clamped to {@code [0,100]} and rounded to 2 decimals.
     */
    @Query(value = "SELECT c.document_id AS documentId, d.file_name AS documentName, c.chunk_text AS chunkText, "
            + "ROUND(LEAST(GREATEST((1 - (c.embedding <=> CAST(:queryVector AS vector))) * 100, 0), 100)::numeric, 2) AS score "
            + "FROM document_chunks c JOIN documents d ON d.id = c.document_id "
            + "WHERE d.status = 'PROCESSED' "
            + "ORDER BY c.embedding <=> CAST(:queryVector AS vector) "
            + "LIMIT :limit", nativeQuery = true)
    List<Object[]> findSimilarChunks(@Param("queryVector") String queryVector, @Param("limit") int limit);
}
