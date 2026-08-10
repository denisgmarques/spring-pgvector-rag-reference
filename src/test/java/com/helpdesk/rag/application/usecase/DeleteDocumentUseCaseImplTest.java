package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.DeleteDocumentUseCase.DeleteDocumentCommand;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentStatus;
import com.helpdesk.rag.domain.exception.DocumentProcessingConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

class DeleteDocumentUseCaseImplTest {

    private DocumentRepositoryPort documentRepositoryPort;
    private DeleteDocumentUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        documentRepositoryPort = mock(DocumentRepositoryPort.class);
        useCase = new DeleteDocumentUseCaseImpl(documentRepositoryPort);
        when(documentRepositoryPort.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Document documentWithStatus(UUID id, DocumentStatus status) {
        Document document = Document.uploadNew(id, "notes.txt", "text/plain", 100L, "content".getBytes(), Instant.now());
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
    void nonProcessingDocument_isSoftDeleted_andChunksRemoved() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId))
                .thenReturn(Optional.of(documentWithStatus(documentId, DocumentStatus.PROCESSED)));

        useCase.delete(new DeleteDocumentCommand(documentId));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DocumentStatus.DELETED);
        assertThat(captor.getValue().getDeletedAt()).isNotNull();

        verify(documentRepositoryPort, times(1)).deleteChunksByDocumentId(documentId);
    }

    @Test
    void processingDocument_isRejected_withNoMutatingRepositoryCalls() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId))
                .thenReturn(Optional.of(documentWithStatus(documentId, DocumentStatus.PROCESSING)));

        assertThatThrownBy(() -> useCase.delete(new DeleteDocumentCommand(documentId)))
                .isInstanceOf(DocumentProcessingConflictException.class);

        verify(documentRepositoryPort, never()).save(any());
        verify(documentRepositoryPort, never()).deleteChunksByDocumentId(any());
    }
}
