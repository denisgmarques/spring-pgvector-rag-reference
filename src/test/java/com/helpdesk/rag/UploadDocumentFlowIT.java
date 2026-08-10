package com.helpdesk.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.rag.support.AbstractIntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test (MockMvc + Testcontainers pgvector + {@code FakeEmbeddingAdapter}) for
 * the upload/post-commit pipeline (RF-01..RF-04): a valid {@code .txt} upload eventually
 * reaches {@code PROCESSED} with vectorized chunks, and a blank-content upload eventually
 * reaches {@code ERROR} with the fixed zero-content message and zero chunks. Eventual
 * consistency of the async listener is awaited via Awaitility rather than a fixed sleep.
 */
class UploadDocumentFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validTxtUpload_eventuallyProcessed_withVectorizedChunks() throws Exception {
        byte[] content = new ClassPathResource("fixtures/sample.txt").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        UUID documentId = extractDocumentId(result);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM documents WHERE id = ?", String.class, documentId);
            assertThat(status).isEqualTo("PROCESSED");
        });

        List<Map<String, Object>> chunkDims = jdbcTemplate.queryForList(
                "SELECT vector_dims(embedding) AS dims FROM document_chunks WHERE document_id = ? ORDER BY chunk_index",
                documentId);

        assertThat(chunkDims).isNotEmpty();
        chunkDims.forEach(row -> assertThat(((Number) row.get("dims")).intValue()).isEqualTo(1536));
    }

    @Test
    void blankContentUpload_eventuallyError_withFixedMessageAndZeroChunks() throws Exception {
        byte[] content = new ClassPathResource("fixtures/blank.txt").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "blank.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        UUID documentId = extractDocumentId(result);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM documents WHERE id = ?", String.class, documentId);
            assertThat(status).isEqualTo("ERROR");
        });

        String errorMessage = jdbcTemplate.queryForObject(
                "SELECT error_message FROM documents WHERE id = ?", String.class, documentId);
        assertThat(errorMessage).isEqualTo("Nenhum conteúdo extraído do documento");

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_id = ?", Integer.class, documentId);
        assertThat(chunkCount).isZero();
    }

    private UUID extractDocumentId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }
}
