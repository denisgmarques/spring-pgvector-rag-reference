package com.helpdesk.rag.application.ports.in;

import com.helpdesk.rag.domain.DocumentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListDocumentsUseCase {

    DocumentPageResult list(ListDocumentsQuery query);

    record ListDocumentsQuery(String cursor) {
    }

    record DocumentSummary(UUID id, String fileName, DocumentStatus status, Instant uploadedAt) {
    }

    record DocumentPageResult(List<DocumentSummary> items, String nextCursor) {
    }
}
