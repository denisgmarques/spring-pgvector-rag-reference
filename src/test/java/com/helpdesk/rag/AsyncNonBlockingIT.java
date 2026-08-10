package com.helpdesk.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import com.helpdesk.rag.infrastructure.adapters.out.ai.FakeEmbeddingAdapter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves RNF-02 verbatim: with a delay-injecting decorator around {@code FakeEmbeddingAdapter}
 * standing in for {@code EmbeddingServicePort}, the HTTP response of a document upload
 * returns before the delayed embedding call completes — i.e. the {@code @Async}
 * + {@code @TransactionalEventListener(AFTER_COMMIT)} post-commit pipeline truly decouples
 * the HTTP thread from extraction/chunking/embedding work, proven by comparing wall-clock
 * timestamps rather than racing on a sleep. Does not extend {@link com.helpdesk.rag.support.AbstractIntegrationTest}
 * because that base class imports the plain (non-delayed) {@code FakeEmbeddingAdapter}
 * config; this test needs its own delayed bean to be the sole {@code EmbeddingServicePort}
 * candidate in its context.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AsyncNonBlockingIT.DelayedEmbeddingConfig.class)
class AsyncNonBlockingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void flywayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmbeddingServicePort embeddingServicePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadResponse_returnsBeforeDelayedEmbeddingCallCompletes() throws Exception {
        DelayedEmbeddingAdapter delayedAdapter = (DelayedEmbeddingAdapter) embeddingServicePort;

        byte[] content = new ClassPathResource("fixtures/sample.txt").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        Instant httpResponseReturnedAt = Instant.now();

        Instant embeddingCallCompletedAt = delayedAdapter.awaitFirstCallCompletion(Duration.ofSeconds(10));

        assertThat(httpResponseReturnedAt).isBefore(embeddingCallCompletedAt);

        // Drain the async pipeline to completion before the test (and the container/context
        // teardown that follows) so no delayed background work is left running afterward.
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID documentId = UUID.fromString(body.get("id").asText());
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM documents WHERE id = ?", String.class, documentId);
            assertThat(status).isIn("PROCESSED", "ERROR");
        });
    }

    @TestConfiguration
    static class DelayedEmbeddingConfig {

        @Bean
        @Primary
        DelayedEmbeddingAdapter delayedEmbeddingAdapter() {
            return new DelayedEmbeddingAdapter();
        }
    }

    /**
     * Decorates {@link FakeEmbeddingAdapter} with an artificial delay, and records the
     * wall-clock instant at which its first {@code embed} call finishes so the test can
     * compare it against the HTTP response timestamp.
     */
    static class DelayedEmbeddingAdapter implements EmbeddingServicePort {

        private static final long ARTIFICIAL_DELAY_MILLIS = 2000L;

        private final EmbeddingServicePort delegate = new FakeEmbeddingAdapter();
        private final CountDownLatch firstCallCompleted = new CountDownLatch(1);
        private volatile Instant firstCallCompletedAt;

        @Override
        public float[] embed(String text) {
            try {
                Thread.sleep(ARTIFICIAL_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            float[] result = delegate.embed(text);
            if (firstCallCompleted.getCount() > 0) {
                firstCallCompletedAt = Instant.now();
                firstCallCompleted.countDown();
            }
            return result;
        }

        Instant awaitFirstCallCompletion(Duration timeout) throws InterruptedException {
            if (!firstCallCompleted.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Delayed embedding call did not complete within " + timeout);
            }
            return firstCallCompletedAt;
        }
    }
}
