package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.UploadDocumentUseCase;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.event.DocumentUploadedEvent;
import com.helpdesk.rag.domain.service.FileValidationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Validates the incoming file (RF-11) before any persistence, then persists the
 * {@code Document} with {@code status=PENDING} and publishes {@link DocumentUploadedEvent}
 * within the same transaction so post-commit processing (RF-02/RF-03/RF-04) only starts
 * after the metadata row is durably committed (RF-01).
 */
public class UploadDocumentUseCaseImpl implements UploadDocumentUseCase {

    private final FileValidationService fileValidationService;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final ApplicationEventPublisher eventPublisher;

    public UploadDocumentUseCaseImpl(FileValidationService fileValidationService,
                                      DocumentRepositoryPort documentRepositoryPort,
                                      ApplicationEventPublisher eventPublisher) {
        this.fileValidationService = fileValidationService;
        this.documentRepositoryPort = documentRepositoryPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public UploadDocumentResult upload(UploadDocumentCommand command) {
        fileValidationService.validate(command.fileName(), command.contentType(), command.fileSize());

        Document document = Document.uploadNew(
                UUID.randomUUID(),
                command.fileName(),
                command.contentType(),
                command.fileSize(),
                command.fileData(),
                Instant.now()
        );
        Document saved = documentRepositoryPort.save(document);

        eventPublisher.publishEvent(new DocumentUploadedEvent(saved.getId(), Instant.now()));

        return new UploadDocumentResult(saved.getId(), saved.getStatus());
    }
}
