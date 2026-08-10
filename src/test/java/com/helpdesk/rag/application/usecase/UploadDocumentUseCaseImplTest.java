package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.UploadDocumentUseCase.UploadDocumentCommand;
import com.helpdesk.rag.application.ports.in.UploadDocumentUseCase.UploadDocumentResult;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentStatus;
import com.helpdesk.rag.domain.event.DocumentUploadedEvent;
import com.helpdesk.rag.domain.exception.DocumentValidationException;
import com.helpdesk.rag.domain.service.FileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadDocumentUseCaseImplTest {

    private final FileValidationService fileValidationService = new FileValidationService();
    private DocumentRepositoryPort documentRepositoryPort;
    private ApplicationEventPublisher eventPublisher;
    private UploadDocumentUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        documentRepositoryPort = mock(DocumentRepositoryPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new UploadDocumentUseCaseImpl(fileValidationService, documentRepositoryPort, eventPublisher);
        when(documentRepositoryPort.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validUpload_persistsPendingDocument_andPublishesEventExactlyOnce() {
        UploadDocumentCommand command = new UploadDocumentCommand("notes.txt", "text/plain", 100L, "hello".getBytes());

        UploadDocumentResult result = useCase.upload(command);

        assertThat(result.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(result.documentId()).isNotNull();
        assertThat(result.fileName()).isEqualTo("notes.txt");
        assertThat(result.uploadedAt()).isNotNull();
        assertThat(result.version()).isEqualTo(0L);
        assertThat(result.errorMessage()).isNull();

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, times(1)).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(documentCaptor.getValue().getFileName()).isEqualTo("notes.txt");

        ArgumentCaptor<DocumentUploadedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(result.documentId());
    }

    @Test
    void invalidExtension_rejectsWithoutPersistence() {
        UploadDocumentCommand command = new UploadDocumentCommand("virus.exe", "application/octet-stream", 100L, "hi".getBytes());

        assertThatThrownBy(() -> useCase.upload(command)).isInstanceOf(DocumentValidationException.class);

        verifyNoInteractions(documentRepositoryPort, eventPublisher);
    }

    @Test
    void oversizedFile_rejectsWithoutPersistence() {
        UploadDocumentCommand command = new UploadDocumentCommand(
                "big.pdf", "application/pdf", FileValidationService.MAX_FILE_SIZE_BYTES + 1, new byte[0]);

        assertThatThrownBy(() -> useCase.upload(command)).isInstanceOf(DocumentValidationException.class);

        verifyNoInteractions(documentRepositoryPort, eventPublisher);
    }

    @Test
    void contentTypeFallback_octetStream_isAccepted() {
        UploadDocumentCommand command = new UploadDocumentCommand("report.pdf", "application/octet-stream", 100L, "hi".getBytes());

        UploadDocumentResult result = useCase.upload(command);

        assertThat(result.status()).isEqualTo(DocumentStatus.PENDING);
        verify(documentRepositoryPort, times(1)).save(any(Document.class));
        verify(eventPublisher, times(1)).publishEvent(any(DocumentUploadedEvent.class));
    }

    @Test
    void contentTypeFallback_missing_isAccepted() {
        UploadDocumentCommand command = new UploadDocumentCommand("report.txt", null, 100L, "hi".getBytes());

        UploadDocumentResult result = useCase.upload(command);

        assertThat(result.status()).isEqualTo(DocumentStatus.PENDING);
        verify(documentRepositoryPort, times(1)).save(any(Document.class));
    }

    @Test
    void contradictoryContentType_rejectsWithoutPersistence() {
        UploadDocumentCommand command = new UploadDocumentCommand("notes.txt", "image/png", 100L, "hi".getBytes());

        assertThatThrownBy(() -> useCase.upload(command)).isInstanceOf(DocumentValidationException.class);

        verifyNoInteractions(documentRepositoryPort, eventPublisher);
    }
}
