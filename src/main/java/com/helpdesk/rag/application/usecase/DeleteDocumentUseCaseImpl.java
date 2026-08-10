package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.DeleteDocumentUseCase;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentStatus;
import com.helpdesk.rag.domain.exception.DocumentProcessingConflictException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Rejects delete requests targeting a {@code PROCESSING} document (RF-07) before any
 * mutation happens. Otherwise performs a soft delete ({@code status=DELETED} +
 * {@code deletedAt}) and physically removes the document's chunks/vectors (RF-08/RF-09).
 */
public class DeleteDocumentUseCaseImpl implements DeleteDocumentUseCase {

    private final DocumentRepositoryPort documentRepositoryPort;

    public DeleteDocumentUseCaseImpl(DocumentRepositoryPort documentRepositoryPort) {
        this.documentRepositoryPort = documentRepositoryPort;
    }

    @Override
    @Transactional
    public void delete(DeleteDocumentCommand command) {
        Document document = documentRepositoryPort.findById(command.documentId())
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + command.documentId()));

        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentProcessingConflictException(
                    "Document " + command.documentId() + " is currently PROCESSING and cannot be deleted");
        }

        document.markDeleted(Instant.now());
        documentRepositoryPort.save(document);
        documentRepositoryPort.deleteChunksByDocumentId(command.documentId());
    }
}
