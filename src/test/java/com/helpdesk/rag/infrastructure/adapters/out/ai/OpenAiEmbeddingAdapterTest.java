package com.helpdesk.rag.infrastructure.adapters.out.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiEmbeddingAdapterTest {

    @Test
    void embed_delegatesInputText_andReturns1536DimensionVector_withNoRealNetworkCall() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        float[] canned = new float[1536];
        when(embeddingModel.embed(eq("what is the refund policy?"))).thenReturn(canned);

        OpenAiEmbeddingAdapter adapter = new OpenAiEmbeddingAdapter(embeddingModel);

        float[] result = adapter.embed("what is the refund policy?");

        assertThat(result).isSameAs(canned);
        assertThat(result).hasSize(1536);
        verify(embeddingModel, times(1)).embed("what is the refund policy?");
    }
}
