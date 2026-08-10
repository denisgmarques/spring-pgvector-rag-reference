package com.helpdesk.rag.infrastructure.adapters.out.ai;

import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;

import java.util.Locale;
import java.util.Random;

/**
 * Deterministic, network-free {@link EmbeddingServicePort} used exclusively by the test
 * suite (CT-06/RNF-04): the same normalized input text always seeds the same
 * {@link Random} sequence, so repeated calls yield byte-identical vectors, which keeps
 * integration-test similarity scores repeatable (RNF-06). Lives under
 * {@code src/test/java} only — never shipped in the production JAR.
 */
public class FakeEmbeddingAdapter implements EmbeddingServicePort {

    private static final int DIMENSIONS = 1536;

    @Override
    public float[] embed(String text) {
        String normalized = normalize(text);
        Random random = new Random(normalized.hashCode());
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = random.nextFloat() * 2f - 1f;
        }
        return vector;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
