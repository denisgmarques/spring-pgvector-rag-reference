package com.helpdesk.rag;

import com.helpdesk.rag.infrastructure.config.TestEmbeddingConfig;
import org.springframework.boot.SpringApplication;

/**
 * Manual local-run entry point that wires {@link TestEmbeddingConfig} (the deterministic
 * {@code FakeEmbeddingAdapter}) in place of the real OpenAI adapter, following the standard
 * Spring Boot pattern for running the app locally against test doubles
 * ({@code mvn spring-boot:test-run}, which prefers a test-sourced main method over the
 * production one). Never packaged — lives under {@code src/test/java} only.
 */
public class TestHelpdeskRagApplication {

    public static void main(String[] args) {
        SpringApplication.from(HelpdeskRagApplication::main)
                .with(TestEmbeddingConfig.class)
                .run(args);
    }
}
