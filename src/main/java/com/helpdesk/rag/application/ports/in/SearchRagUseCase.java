package com.helpdesk.rag.application.ports.in;

import java.util.List;
import java.util.UUID;

public interface SearchRagUseCase {

    List<SearchResult> search(SearchQuery query);

    record SearchQuery(String question) {
    }

    record SearchResult(UUID documentId, String documentName, String chunkText, double score) {
    }
}
