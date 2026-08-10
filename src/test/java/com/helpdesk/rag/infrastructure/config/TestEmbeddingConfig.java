package com.helpdesk.rag.infrastructure.config;

import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import com.helpdesk.rag.infrastructure.adapters.out.ai.FakeEmbeddingAdapter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Wires {@link FakeEmbeddingAdapter} as the active {@link EmbeddingServicePort} bean for
 * the {@code test} profile (CT-06). {@code @TestConfiguration} classes are excluded from
 * regular component scanning, so integration tests must {@code @Import} this explicitly.
 */
@TestConfiguration
@Profile("test")
public class TestEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingServicePort embeddingServicePort() {
        return new FakeEmbeddingAdapter();
    }
}
