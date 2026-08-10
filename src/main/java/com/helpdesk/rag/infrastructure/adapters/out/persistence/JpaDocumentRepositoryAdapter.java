package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;
import com.pgvector.PGvector;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements {@link DocumentRepositoryPort} on top of Spring Data JPA. Listing uses
 * keyset pagination (opaque cursor encoding {@code uploadedAt|id}) ordered by
 * {@code uploaded_at DESC, id DESC}, excluding {@code DELETED} (RF-05/CT-02). Semantic
 * search uses a native pgvector cosine-distance query restricted to {@code PROCESSED}
 * documents, with the score clamp/round formula owned entirely by the SQL (RF-10).
 * {@code saveAndFlush} is used for documents so a stale {@code @Version} surfaces as
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} synchronously
 * within this method call (RF-12), where Spring Data's repository-proxy exception
 * translation applies.
 */
@Repository
public class JpaDocumentRepositoryAdapter implements DocumentRepositoryPort {

    private static final char CURSOR_DELIMITER = '|';

    private final SpringDataDocumentJpaRepository documentJpaRepository;
    private final SpringDataDocumentChunkJpaRepository chunkJpaRepository;
    private final DocumentPersistenceMapper mapper;

    public JpaDocumentRepositoryAdapter(SpringDataDocumentJpaRepository documentJpaRepository,
                                         SpringDataDocumentChunkJpaRepository chunkJpaRepository,
                                         DocumentPersistenceMapper mapper) {
        this.documentJpaRepository = documentJpaRepository;
        this.chunkJpaRepository = chunkJpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Document save(Document document) {
        DocumentJpaEntity entity = mapper.toEntity(document);
        DocumentJpaEntity saved = documentJpaRepository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return documentJpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public DocumentBatch findBatch(String cursor, int size) {
        List<DocumentJpaEntity> rows = (cursor == null || cursor.isBlank())
                ? documentJpaRepository.findFirstBatch(size + 1)
                : fetchNextBatch(cursor, size);

        boolean hasMore = rows.size() > size;
        List<DocumentJpaEntity> pageRows = hasMore ? rows.subList(0, size) : rows;

        List<Document> items = pageRows.stream().map(mapper::toDomain).collect(Collectors.toList());
        String nextCursor = hasMore ? encodeCursor(pageRows.get(pageRows.size() - 1)) : null;

        return new DocumentBatch(items, nextCursor);
    }

    private List<DocumentJpaEntity> fetchNextBatch(String cursor, int size) {
        Cursor decoded = Cursor.decode(cursor);
        return documentJpaRepository.findNextBatch(decoded.uploadedAt(), decoded.id(), size + 1);
    }

    private String encodeCursor(DocumentJpaEntity entity) {
        return new Cursor(entity.getUploadedAt(), entity.getId()).encode();
    }

    @Override
    @Transactional
    public void deleteChunksByDocumentId(UUID documentId) {
        chunkJpaRepository.deleteByDocumentId(documentId);
    }

    @Override
    @Transactional
    public void saveChunks(List<DocumentChunk> chunks) {
        List<DocumentChunkJpaEntity> entities = chunks.stream().map(mapper::toEntity).collect(Collectors.toList());
        chunkJpaRepository.saveAll(entities);
    }

    @Override
    public List<SimilarChunk> findSimilarChunks(float[] queryVector, int limit) {
        String vectorLiteral = new PGvector(queryVector).toString();
        List<Object[]> rows = chunkJpaRepository.findSimilarChunks(vectorLiteral, limit);
        return rows.stream()
                .map(row -> new SimilarChunk(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).doubleValue()))
                .collect(Collectors.toList());
    }

    private record Cursor(Instant uploadedAt, UUID id) {

        String encode() {
            String raw = uploadedAt.toString() + CURSOR_DELIMITER + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int delimiterIndex = raw.lastIndexOf(CURSOR_DELIMITER);
            Instant uploadedAt = Instant.parse(raw.substring(0, delimiterIndex));
            UUID id = UUID.fromString(raw.substring(delimiterIndex + 1));
            return new Cursor(uploadedAt, id);
        }
    }
}
