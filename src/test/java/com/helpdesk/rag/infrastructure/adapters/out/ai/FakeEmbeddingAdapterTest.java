package com.helpdesk.rag.infrastructure.adapters.out.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeEmbeddingAdapterTest {

    private final FakeEmbeddingAdapter adapter = new FakeEmbeddingAdapter();

    @Test
    void sameInputText_producesIdenticalVectors_acrossRepeatedCalls() {
        float[] first = adapter.embed("How do I reset my password?");
        float[] second = adapter.embed("How do I reset my password?");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void output_hasExactly1536Elements() {
        float[] vector = adapter.embed("some question");

        assertThat(vector).hasSize(1536);
    }

    @Test
    void differentInputText_producesDifferentVectors() {
        float[] first = adapter.embed("question one");
        float[] second = adapter.embed("question two");

        assertThat(second).isNotEqualTo(first);
    }
}
