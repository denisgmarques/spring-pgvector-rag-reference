package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.UpdateDocumentUseCase;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentStatus;
import com.helpdesk.rag.domain.event.DocumentUpdatedEvent;
import com.helpdesk.rag.domain.exception.DocumentProcessingConflictException;
import com.helpdesk.rag.domain.service.FileValidationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Rejects update requests targeting a {@code PROCESSING} document (RF-07) before any
 * validation/mutation. Otherwise validates the replacement file (RF-11, shared with
 * upload), replaces the stored file/metadata, transitions to {@code PROCESSING}, and
 * publishes {@link DocumentUpdatedEvent} for post-commit reprocessing (RF-06).
 */
public class UpdateDocumentUseCaseImpl implements UpdateDocumentUseCase {

    private final FileValidationService fileValidationService;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateDocumentUseCaseImpl(FileValidationService fileValidationService,
                                      DocumentRepositoryPort documentRepositoryPort,
                                      ApplicationEventPublisher eventPublisher) {
        this.fileValidationService = fileValidationService;
        this.documentRepositoryPort = documentRepositoryPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public UpdateDocumentResult update(UpdateDocumentCommand command) {
        Document document = documentRepositoryPort.findById(command.documentId())
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + command.documentId()));

        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new DocumentProcessingConflictException(
                    "Document " + command.documentId() + " is currently PROCESSING and cannot be updated");
        }

        fileValidationService.validate(command.fileName(), command.contentType(), command.fileSize());

        document.replaceFile(command.fileName(), command.contentType(), command.fileSize(), command.fileData());
        document.markProcessing();
        Document saved = documentRepositoryPort.save(document);

        eventPublisher.publishEvent(new DocumentUpdatedEvent(saved.getId(), Instant.now()));

        return new UpdateDocumentResult(saved.getId(), saved.getFileName(), saved.getStatus(), saved.getErrorMessage(),
                saved.getUploadedAt(), saved.getUpdatedAt(), saved.getVersion());
    }
}
