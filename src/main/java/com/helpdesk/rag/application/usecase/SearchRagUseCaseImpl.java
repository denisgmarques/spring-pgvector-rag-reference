package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.SearchRagUseCase;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort.SimilarChunk;
import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Embeds the question exactly once, then delegates to the native similarity query
 * (T16), which owns score calculation and ordering. Results are mapped straight
 * through without re-sorting or recomputation (RF-10).
 */
public class SearchRagUseCaseImpl implements SearchRagUseCase {

    private static final int RESULT_LIMIT = 10;

    private final EmbeddingServicePort embeddingServicePort;
    private final DocumentRepositoryPort documentRepositoryPort;

    public SearchRagUseCaseImpl(EmbeddingServicePort embeddingServicePort, DocumentRepositoryPort documentRepositoryPort) {
        this.embeddingServicePort = embeddingServicePort;
        this.documentRepositoryPort = documentRepositoryPort;
    }

    @Override
    public List<SearchResult> search(SearchQuery query) {
        float[] questionVector = embeddingServicePort.embed(query.question());
        List<SimilarChunk> similarChunks = documentRepositoryPort.findSimilarChunks(questionVector, RESULT_LIMIT);

        return similarChunks.stream()
                .map(chunk -> new SearchResult(chunk.documentId(), chunk.documentName(), chunk.chunkText(), chunk.score()))
                .collect(Collectors.toList());
    }
}
