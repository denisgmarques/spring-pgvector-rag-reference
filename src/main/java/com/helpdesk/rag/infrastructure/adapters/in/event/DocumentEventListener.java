package com.helpdesk.rag.infrastructure.adapters.in.event;

import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import com.helpdesk.rag.application.ports.out.TextExtractionPort;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;
import com.helpdesk.rag.domain.event.DocumentUpdatedEvent;
import com.helpdesk.rag.domain.event.DocumentUploadedEvent;
import com.helpdesk.rag.domain.service.ChunkingService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Post-commit processing pipeline (RF-02/RF-03/RF-04/RF-06): runs on a dedicated
 * executor ({@code documentProcessingExecutor}) only after the triggering transaction
 * commits (RNF-02), so the HTTP upload/update thread never waits on extraction,
 * chunking, or embedding generation. Chunks are only persisted as a single batch after
 * every embedding for the document has succeeded, so a mid-processing failure never
 * leaves orphan {@code document_chunks} rows behind (RF-04).
 */
@Component
public class DocumentEventListener {

    static final String ZERO_CONTENT_ERROR_MESSAGE = "Nenhum conteúdo extraído do documento";

    private final DocumentRepositoryPort documentRepositoryPort;
    private final TextExtractionPort textExtractionPort;
    private final ChunkingService chunkingService;
    private final EmbeddingServicePort embeddingServicePort;

    public DocumentEventListener(DocumentRepositoryPort documentRepositoryPort,
                                  TextExtractionPort textExtractionPort,
                                  ChunkingService chunkingService,
                                  EmbeddingServicePort embeddingServicePort) {
        this.documentRepositoryPort = documentRepositoryPort;
        this.textExtractionPort = textExtractionPort;
        this.chunkingService = chunkingService;
        this.embeddingServicePort = embeddingServicePort;
    }

    @Async("documentProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        process(event.documentId(), false);
    }

    @Async("documentProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUpdated(DocumentUpdatedEvent event) {
        process(event.documentId(), true);
    }

    private void process(UUID documentId, boolean deleteExistingChunksFirst) {
        Document document = documentRepositoryPort.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));

        document.markProcessing();
        document = documentRepositoryPort.save(document);

        if (deleteExistingChunksFirst) {
            documentRepositoryPort.deleteChunksByDocumentId(documentId);
        }

        try {
            String extractedText = textExtractionPort.extractText(document.getFileData(), document.getContentType());
            List<String> chunkTexts = chunkingService.chunk(extractedText);

            if (chunkTexts.isEmpty()) {
                document.markError(ZERO_CONTENT_ERROR_MESSAGE);
                documentRepositoryPort.save(document);
                return;
            }

            List<DocumentChunk> chunks = embedChunks(documentId, chunkTexts);

            documentRepositoryPort.saveChunks(chunks);
            document.markProcessed();
            documentRepositoryPort.save(document);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            document.markError(message);
            documentRepositoryPort.save(document);
        }
    }

    private List<DocumentChunk> embedChunks(UUID documentId, List<String> chunkTexts) {
        List<DocumentChunk> chunks = new ArrayList<>(chunkTexts.size());
        Instant now = Instant.now();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String chunkText = chunkTexts.get(i);
            float[] embedding = embeddingServicePort.embed(chunkText);
            chunks.add(new DocumentChunk(UUID.randomUUID(), documentId, i, chunkText, embedding, now));
        }
        return chunks;
    }
}
