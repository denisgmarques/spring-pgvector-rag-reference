package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.ListDocumentsUseCase;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort.DocumentBatch;
import com.helpdesk.rag.domain.Document;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thin orchestration over {@link DocumentRepositoryPort#findBatch}: the fixed batch
 * size (20 — RNF-07), exclusion of {@code DELETED}, ordering, and cursor computation
 * are all owned by the repository/keyset query (T16); this use case only maps rows to
 * DTOs and forwards the cursor unchanged (RF-05).
 */
public class ListDocumentsUseCaseImpl implements ListDocumentsUseCase {

    private static final int BATCH_SIZE = 20;

    private final DocumentRepositoryPort documentRepositoryPort;

    public ListDocumentsUseCaseImpl(DocumentRepositoryPort documentRepositoryPort) {
        this.documentRepositoryPort = documentRepositoryPort;
    }

    @Override
    public DocumentPageResult list(ListDocumentsQuery query) {
        DocumentBatch batch = documentRepositoryPort.findBatch(query.cursor(), BATCH_SIZE);

        List<DocumentSummary> summaries = batch.items().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        return new DocumentPageResult(summaries, batch.nextCursor());
    }

    private DocumentSummary toSummary(Document document) {
        return new DocumentSummary(document.getId(), document.getFileName(), document.getStatus(), document.getUploadedAt());
    }
}
