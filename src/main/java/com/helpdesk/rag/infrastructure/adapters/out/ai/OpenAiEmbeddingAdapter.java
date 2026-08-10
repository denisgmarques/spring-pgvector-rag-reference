package com.helpdesk.rag.infrastructure.adapters.out.ai;

import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production {@link EmbeddingServicePort} backed by Spring AI's {@link EmbeddingModel}
 * abstraction, configured for OpenAI {@code text-embedding-3-small} (1536 dims, CT-06).
 * The API key is read from {@code OPENAI_API_KEY} via {@code application.yml}/
 * {@code application-prod.yml}, never hardcoded. Excluded from the {@code test} profile
 * so the automated suite never instantiates it (RNF-04) — {@code FakeEmbeddingAdapter}
 * is wired instead.
 */
@Component
@Profile("!test")
public class OpenAiEmbeddingAdapter implements EmbeddingServicePort {

    private final EmbeddingModel embeddingModel;

    public OpenAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}
