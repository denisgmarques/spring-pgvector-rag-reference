package com.helpdesk.rag.infrastructure.adapters.in.event;

import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import com.helpdesk.rag.application.ports.out.TextExtractionPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;
import com.helpdesk.rag.domain.DocumentStatus;
import com.helpdesk.rag.domain.event.DocumentUpdatedEvent;
import com.helpdesk.rag.domain.event.DocumentUploadedEvent;
import com.helpdesk.rag.domain.service.ChunkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentEventListenerTest {

    private DocumentRepositoryPort documentRepositoryPort;
    private TextExtractionPort textExtractionPort;
    private ChunkingService chunkingService;
    private EmbeddingServicePort embeddingServicePort;
    private DocumentEventListener listener;

    @BeforeEach
    void setUp() {
        documentRepositoryPort = mock(DocumentRepositoryPort.class);
        textExtractionPort = mock(TextExtractionPort.class);
        chunkingService = new ChunkingService();
        embeddingServicePort = mock(EmbeddingServicePort.class);
        listener = new DocumentEventListener(documentRepositoryPort, textExtractionPort, chunkingService, embeddingServicePort);

        when(documentRepositoryPort.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(embeddingServicePort.embed(anyString())).thenReturn(new float[1536]);
    }

    private Document pendingDocument(UUID id) {
        return Document.uploadNew(id, "notes.txt", "text/plain", 100L, "content".getBytes(), Instant.now());
    }

    @Test
    void uploadEvent_success_producesChunksWithFullDimensionEmbeddings_andMarksProcessed() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId)).thenReturn(Optional.of(pendingDocument(documentId)));
        when(textExtractionPort.extractText(any(), any())).thenReturn("Paragraph one.\n\nParagraph two.");

        listener.onDocumentUploaded(new DocumentUploadedEvent(documentId, Instant.now()));

        ArgumentCaptor<List<DocumentChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepositoryPort, times(1)).saveChunks(chunksCaptor.capture());
        List<DocumentChunk> savedChunks = chunksCaptor.getValue();
        assertThat(savedChunks).isNotEmpty();
        savedChunks.forEach(chunk -> assertThat(chunk.getEmbedding()).hasSize(1536));

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, times(2)).save(documentCaptor.capture());
        Document finalState = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(DocumentStatus.PROCESSED);
    }

    @Test
    void uploadEvent_zeroChunks_marksErrorWithFixedMessage_andPersistsNoChunks() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId)).thenReturn(Optional.of(pendingDocument(documentId)));
        when(textExtractionPort.extractText(any(), any())).thenReturn("   ");

        listener.onDocumentUploaded(new DocumentUploadedEvent(documentId, Instant.now()));

        verify(documentRepositoryPort, never()).saveChunks(any());

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, times(2)).save(documentCaptor.capture());
        Document finalState = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(DocumentStatus.ERROR);
        assertThat(finalState.getErrorMessage()).isEqualTo("Nenhum conteúdo extraído do documento");
    }

    @Test
    void uploadEvent_exceptionDuringProcessing_marksError_withoutOrphanChunks() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId)).thenReturn(Optional.of(pendingDocument(documentId)));
        when(textExtractionPort.extractText(any(), any())).thenThrow(new RuntimeException("extraction failed"));

        listener.onDocumentUploaded(new DocumentUploadedEvent(documentId, Instant.now()));

        verify(documentRepositoryPort, never()).saveChunks(any());

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, times(2)).save(documentCaptor.capture());
        Document finalState = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(DocumentStatus.ERROR);
        assertThat(finalState.getErrorMessage()).isEqualTo("extraction failed");
    }

    @Test
    void updateEvent_deletesOldChunks_beforeSavingNewChunks() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId)).thenReturn(Optional.of(pendingDocument(documentId)));
        when(textExtractionPort.extractText(any(), any())).thenReturn("Paragraph one.\n\nParagraph two.");

        listener.onDocumentUpdated(new DocumentUpdatedEvent(documentId, Instant.now()));

        InOrder inOrder = inOrder(documentRepositoryPort);
        inOrder.verify(documentRepositoryPort).deleteChunksByDocumentId(documentId);
        inOrder.verify(documentRepositoryPort).saveChunks(any());
    }
}
