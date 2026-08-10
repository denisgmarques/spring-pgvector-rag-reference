package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.UpdateDocumentUseCase.UpdateDocumentCommand;
import com.helpdesk.rag.application.ports.in.UpdateDocumentUseCase.UpdateDocumentResult;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentStatus;
import com.helpdesk.rag.domain.event.DocumentUpdatedEvent;
import com.helpdesk.rag.domain.exception.DocumentProcessingConflictException;
import com.helpdesk.rag.domain.exception.DocumentValidationException;
import com.helpdesk.rag.domain.service.FileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateDocumentUseCaseImplTest {

    private final FileValidationService fileValidationService = new FileValidationService();
    private DocumentRepositoryPort documentRepositoryPort;
    private ApplicationEventPublisher eventPublisher;
    private UpdateDocumentUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        documentRepositoryPort = mock(DocumentRepositoryPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new UpdateDocumentUseCaseImpl(fileValidationService, documentRepositoryPort, eventPublisher);
        when(documentRepositoryPort.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Document documentWithStatus(UUID id, DocumentStatus status) {
        Document document = Document.uploadNew(id, "old.txt", "text/plain", 50L, "old".getBytes(), Instant.now());
        if (status == DocumentStatus.PROCESSING) {
            document.markProcessing();
        } else if (status == DocumentStatus.PROCESSED) {
            document.markProcessing();
            document.markProcessed();
        } else if (status == DocumentStatus.ERROR) {
            document.markProcessing();
            document.markError("boom");
        }
        return document;
    }

    @Test
    void validUpdate_onNonProcessingDocument_setsProcessing_persists_andPublishesEvent() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId))
                .thenReturn(Optional.of(documentWithStatus(documentId, DocumentStatus.PROCESSED)));
        UpdateDocumentCommand command = new UpdateDocumentCommand(documentId, "new.txt", "text/plain", 80L, "new".getBytes());

        UpdateDocumentResult result = useCase.update(command);

        assertThat(result.status()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(result.fileName()).isEqualTo("new.txt");
        assertThat(result.uploadedAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(captor.getValue().getFileName()).isEqualTo("new.txt");

        ArgumentCaptor<DocumentUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentUpdatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(documentId);
    }

    @Test
    void processingDocument_isRejected_withNoMutation() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId))
                .thenReturn(Optional.of(documentWithStatus(documentId, DocumentStatus.PROCESSING)));
        UpdateDocumentCommand command = new UpdateDocumentCommand(documentId, "new.txt", "text/plain", 80L, "new".getBytes());

        assertThatThrownBy(() -> useCase.update(command)).isInstanceOf(DocumentProcessingConflictException.class);

        verify(documentRepositoryPort, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void invalidReplacementFile_isRejected_withNoEventPublished() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId))
                .thenReturn(Optional.of(documentWithStatus(documentId, DocumentStatus.PROCESSED)));
        UpdateDocumentCommand command = new UpdateDocumentCommand(documentId, "malware.exe", "application/octet-stream", 80L, "new".getBytes());

        assertThatThrownBy(() -> useCase.update(command)).isInstanceOf(DocumentValidationException.class);

        verify(documentRepositoryPort, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
