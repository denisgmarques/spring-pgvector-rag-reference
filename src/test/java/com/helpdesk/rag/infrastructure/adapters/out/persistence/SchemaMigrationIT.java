package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import com.helpdesk.rag.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the outcome of {@code V1__init_schema.sql} against a real
 * {@code pgvector/pgvector:pg16} container (RNF-03): the {@code vector} extension is
 * installed, both tables exist with the specified columns, and an HNSW index with the
 * {@code vector_cosine_ops} operator class exists on {@code document_chunks.embedding}.
 */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void vectorExtension_isInstalled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void documentsTable_hasSpecifiedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'documents'", String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "file_name", "content_type", "file_size", "file_data", "status",
                "error_message", "uploaded_at", "updated_at", "deleted_at", "version");
    }

    @Test
    void documentChunksTable_hasSpecifiedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'document_chunks'", String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "document_id", "chunk_index", "chunk_text", "embedding", "created_at");
    }

    @Test
    void hnswIndex_withVectorCosineOps_existsOnEmbeddingColumn() {
        List<String> indexDefs = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'document_chunks'", String.class);

        assertThat(indexDefs).anySatisfy(def -> {
            assertThat(def).containsIgnoringCase("USING hnsw");
            assertThat(def).contains("vector_cosine_ops");
            assertThat(def).contains("embedding");
        });
    }
}
