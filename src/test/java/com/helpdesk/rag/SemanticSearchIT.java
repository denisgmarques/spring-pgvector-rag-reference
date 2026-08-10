package com.helpdesk.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;
import com.helpdesk.rag.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test (MockMvc + Testcontainers pgvector + {@code FakeEmbeddingAdapter}) for
 * semantic search (RF-09/RF-10): seeds two {@code PROCESSED} documents plus one
 * {@code DELETED} document whose chunk text is an exact duplicate of the top match (so
 * its absence from the results proves the status filter, not mere dissimilarity, drives
 * the exclusion), then asserts every returned score sits in {@code [0,100]} with exactly
 * 2 decimals, results are ordered by score descending, and the deleted document never
 * appears.
 */
class SemanticSearchIT extends AbstractIntegrationTest {

    private static final String TOP_CHUNK_TEXT = "How to reset your password using the admin console.";
    private static final String OTHER_CHUNK_TEXT = "Billing cycles run on the first day of every month.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepositoryPort documentRepositoryPort;

    @Autowired
    private EmbeddingServicePort embeddingServicePort;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void search_returnsScoresInRangeOrderedDesc_excludingDeletedDocument() throws Exception {
        UUID topDocumentId = seedDocument("guide.txt", TOP_CHUNK_TEXT, false);
        UUID otherDocumentId = seedDocument("billing.txt", OTHER_CHUNK_TEXT, false);
        UUID deletedDocumentId = seedDocument("secret.txt", TOP_CHUNK_TEXT, true);

        String requestBody = objectMapper.writeValueAsString(Map.of("question", TOP_CHUNK_TEXT));

        MvcResult result = mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode results = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(results.isArray()).isTrue();
        assertThat(results.size()).isEqualTo(2);

        double previousScore = Double.MAX_VALUE;
        for (JsonNode node : results) {
            double score = node.get("score").asDouble();
            assertThat(score).isBetween(0.0, 100.0);
            assertThat(Math.rint(score * 100)).isEqualTo(score * 100);
            assertThat(score).isLessThanOrEqualTo(previousScore);
            previousScore = score;
            assertThat(node.get("documentId").asText()).isNotEqualTo(deletedDocumentId.toString());
        }

        assertThat(results.get(0).get("documentId").asText()).isEqualTo(topDocumentId.toString());
        assertThat(results.get(0).get("score").asDouble()).isEqualTo(100.0);
        assertThat(results.get(1).get("documentId").asText()).isEqualTo(otherDocumentId.toString());
    }

    private UUID seedDocument(String fileName, String chunkText, boolean deleted) {
        UUID id = UUID.randomUUID();
        byte[] data = chunkText.getBytes();
        Document document = Document.uploadNew(id, fileName, "text/plain", data.length, data, Instant.now());
        document.markProcessing();
        document.markProcessed();
        if (deleted) {
            document.markDeleted(Instant.now());
        }
        documentRepositoryPort.save(document);

        float[] embedding = embeddingServicePort.embed(chunkText);
        documentRepositoryPort.saveChunks(
                List.of(new DocumentChunk(UUID.randomUUID(), id, 0, chunkText, embedding, Instant.now())));
        return id;
    }
}
