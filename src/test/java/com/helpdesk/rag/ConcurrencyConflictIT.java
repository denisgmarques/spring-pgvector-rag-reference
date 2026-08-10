package com.helpdesk.rag;

import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;
import com.helpdesk.rag.infrastructure.adapters.out.persistence.JpaDocumentRepositoryAdapter;
import com.helpdesk.rag.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two distinct concurrency-conflict scenarios (RF-07/RF-12), full-stack against a real
 * Testcontainers pgvector database:
 * <ul>
 *   <li>Scenario A — update/delete targeting a {@code PROCESSING} document is rejected
 *       with 409 via {@link com.helpdesk.rag.domain.exception.DocumentProcessingConflictException}
 *       before any mutation, without ever reaching the persistence layer's version check.</li>
 *   <li>Scenario B — a stale in-memory {@code @Version} snapshot (captured before another
 *       writer commits a change) is rejected with 409 via a genuine
 *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException} raised by
 *       real Hibernate flush semantics, never a {@code DocumentProcessingConflictException}.
 *       Since {@link com.helpdesk.rag.infrastructure.adapters.in.web.DocumentController}'s
 *       update/delete endpoints never accept a client-supplied version, the only faithful way
 *       to reproduce a genuinely stale read is to intercept the use case's internal
 *       {@code findById} call (via {@link StaleReadDocumentRepositoryPort}, a thin delegating
 *       wrapper around the real repository adapter) so it returns a snapshot captured before a
 *       concurrent writer's commit, while every other call — including the failing {@code save}
 *       that triggers the real optimistic-lock exception — still executes against the real
 *       database.</li>
 * </ul>
 */
@Import(ConcurrencyConflictIT.StaleReadRepositoryConfig.class)
class ConcurrencyConflictIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentRepositoryPort documentRepositoryPort;

    @Test
    void scenarioA_updateAndDelete_onProcessingDocument_return409WithoutMutation() throws Exception {
        UUID documentId = seedDocument(Document::markProcessing);

        MockMultipartFile file = new MockMultipartFile("file", "v2.txt", "text/plain", "content".getBytes());
        mockMvc.perform(multipart(PUT, "/api/documents/{id}", documentId).file(file))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isConflict());

        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM documents WHERE id = ?", String.class, documentId);
        Long persistedVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM documents WHERE id = ?", Long.class, documentId);

        assertThat(persistedStatus).isEqualTo("PROCESSING");
        assertThat(persistedVersion).isEqualTo(0L);
    }

    @Test
    void scenarioB_updateWithStaleVersion_returns409ViaRealOptimisticLock_distinctFromScenarioA() throws Exception {
        UUID documentId = seedDocument(document -> { });

        // "Request 1" reads the document (version 0) and holds onto this stale snapshot.
        Document staleSnapshot = documentRepositoryPort.findById(documentId).orElseThrow();
        assertThat(staleSnapshot.getVersion()).isEqualTo(0L);

        // A concurrent writer reads + mutates + commits first, bumping the persisted version to 1.
        Document concurrentWrite = documentRepositoryPort.findById(documentId).orElseThrow();
        concurrentWrite.markProcessing();
        documentRepositoryPort.save(concurrentWrite);

        Long bumpedVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM documents WHERE id = ?", Long.class, documentId);
        assertThat(bumpedVersion).isEqualTo(1L);

        // "Request 1" now submits its update, carrying the stale (pre-bump) version: its own
        // internal findById is intercepted to return the stale snapshot instead of a fresh read.
        StaleReadDocumentRepositoryPort staleReadPort = (StaleReadDocumentRepositoryPort) documentRepositoryPort;
        staleReadPort.stubFindById(documentId, staleSnapshot);

        MockMultipartFile file = new MockMultipartFile("file", "v2.txt", "text/plain", "content".getBytes());
        mockMvc.perform(multipart(PUT, "/api/documents/{id}", documentId).file(file))
                .andExpect(status().isConflict());
    }

    private UUID seedDocument(Consumer<Document> transitions) {
        UUID id = UUID.randomUUID();
        byte[] data = "seed content".getBytes();
        Document document = Document.uploadNew(id, "seed.txt", "text/plain", data.length, data, Instant.now());
        transitions.accept(document);
        documentRepositoryPort.save(document);
        return id;
    }

    @TestConfiguration
    static class StaleReadRepositoryConfig {

        @Bean
        @Primary
        StaleReadDocumentRepositoryPort staleReadDocumentRepositoryPort(JpaDocumentRepositoryAdapter real) {
            return new StaleReadDocumentRepositoryPort(real);
        }
    }

    /**
     * Thin delegating wrapper that lets a single test-controlled {@code findById(id)} call
     * return a caller-supplied (potentially stale) snapshot instead of a fresh read, while
     * every other operation — critically, {@code save}, where the real optimistic-lock
     * exception is raised — passes straight through to the real repository adapter.
     */
    static class StaleReadDocumentRepositoryPort implements DocumentRepositoryPort {

        private final DocumentRepositoryPort delegate;
        private volatile UUID staleId;
        private volatile Document staleDocument;

        StaleReadDocumentRepositoryPort(DocumentRepositoryPort delegate) {
            this.delegate = delegate;
        }

        void stubFindById(UUID id, Document document) {
            this.staleId = id;
            this.staleDocument = document;
        }

        @Override
        public Optional<Document> findById(UUID id) {
            if (id.equals(staleId)) {
                return Optional.of(staleDocument);
            }
            return delegate.findById(id);
        }

        @Override
        public Document save(Document document) {
            return delegate.save(document);
        }

        @Override
        public DocumentBatch findBatch(String cursor, int size) {
            return delegate.findBatch(cursor, size);
        }

        @Override
        public void deleteChunksByDocumentId(UUID documentId) {
            delegate.deleteChunksByDocumentId(documentId);
        }

        @Override
        public void saveChunks(List<DocumentChunk> chunks) {
            delegate.saveChunks(chunks);
        }

        @Override
        public List<SimilarChunk> findSimilarChunks(float[] queryVector, int limit) {
            return delegate.findSimilarChunks(queryVector, limit);
        }
    }
}
