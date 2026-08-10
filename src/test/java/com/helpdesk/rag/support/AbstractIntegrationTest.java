package com.helpdesk.rag.support;

import com.helpdesk.rag.infrastructure.config.TestEmbeddingConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for Testcontainers-backed integration tests (RNF-06). Uses the singleton
 * container pattern — a single {@code pgvector/pgvector:pg16} container started once via
 * a static initializer and left running for the JVM's lifetime — instead of a new
 * container per test class, so the whole integration suite runs against one instance.
 * The {@code test} profile is active, and {@link TestEmbeddingConfig} wires the
 * deterministic {@code FakeEmbeddingAdapter} (RNF-04) in place of the real OpenAI
 * adapter, so no network access or {@code OPENAI_API_KEY} is required.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestEmbeddingConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
