package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.SearchRagUseCase.SearchQuery;
import com.helpdesk.rag.application.ports.in.SearchRagUseCase.SearchResult;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort.SimilarChunk;
import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchRagUseCaseImplTest {

    private EmbeddingServicePort embeddingServicePort;
    private DocumentRepositoryPort documentRepositoryPort;
    private SearchRagUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        embeddingServicePort = mock(EmbeddingServicePort.class);
        documentRepositoryPort = mock(DocumentRepositoryPort.class);
        useCase = new SearchRagUseCaseImpl(embeddingServicePort, documentRepositoryPort);
    }

    @Test
    void question_isEmbeddedExactlyOnce() {
        float[] vector = new float[1536];
        when(embeddingServicePort.embed(anyString())).thenReturn(vector);
        when(documentRepositoryPort.findSimilarChunks(any(), anyInt())).thenReturn(List.of());

        useCase.search(new SearchQuery("how do I reset my password?"));

        verify(embeddingServicePort, times(1)).embed("how do I reset my password?");
    }

    @Test
    void portResults_passThroughUnchanged_inSameOrder() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        List<SimilarChunk> canned = List.of(
                new SimilarChunk(doc1, "doc-one.txt", "chunk from doc one", 95.12),
                new SimilarChunk(doc2, "doc-two.txt", "chunk from doc two", 42.50)
        );
        when(embeddingServicePort.embed(anyString())).thenReturn(new float[1536]);
        when(documentRepositoryPort.findSimilarChunks(any(), anyInt())).thenReturn(canned);

        List<SearchResult> results = useCase.search(new SearchQuery("question"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).documentId()).isEqualTo(doc1);
        assertThat(results.get(0).score()).isEqualTo(95.12);
        assertThat(results.get(1).documentId()).isEqualTo(doc2);
        assertThat(results.get(1).score()).isEqualTo(42.50);
    }

    @Test
    void emptyPortResult_mapsToEmptyList() {
        when(embeddingServicePort.embed(anyString())).thenReturn(new float[1536]);
        when(documentRepositoryPort.findSimilarChunks(any(), anyInt())).thenReturn(List.of());

        List<SearchResult> results = useCase.search(new SearchQuery("question"));

        assertThat(results).isEmpty();
    }
}
