package com.helpdesk.rag.infrastructure.adapters.in.web;

import com.helpdesk.rag.application.ports.in.SearchRagUseCase;
import com.helpdesk.rag.application.ports.in.SearchRagUseCase.SearchQuery;
import com.helpdesk.rag.application.ports.in.SearchRagUseCase.SearchResult;
import com.helpdesk.rag.infrastructure.adapters.in.web.dto.SearchRequest;
import com.helpdesk.rag.infrastructure.adapters.in.web.dto.SearchResultResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements CT-05/RF-10: converts the free-text question into embeddings and native
 * pgvector cosine search results via {@link SearchRagUseCase}, returning them as a bare
 * JSON array already ordered by score descending by the use case.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchRagUseCase searchRagUseCase;

    public SearchController(SearchRagUseCase searchRagUseCase) {
        this.searchRagUseCase = searchRagUseCase;
    }

    @PostMapping
    public List<SearchResultResponse> search(@Valid @RequestBody SearchRequest request) {
        List<SearchResult> results = searchRagUseCase.search(new SearchQuery(request.question()));
        return results.stream()
                .map(r -> new SearchResultResponse(r.documentId(), r.documentName(), r.chunkText(), r.score()))
                .collect(Collectors.toList());
    }
}
