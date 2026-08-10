package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;
import com.helpdesk.rag.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentPersistenceMapperTest {

    private final DocumentPersistenceMapper mapper = new DocumentPersistenceMapper();

    @Test
    void roundTrip_processedDocument_preservesAllFields() {
        Instant uploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant updatedAt = uploadedAt.plusSeconds(30);
        Document document = new Document(
                UUID.randomUUID(),
                "report.pdf",
                "application/pdf",
                12_345L,
                new byte[]{1, 2, 3, 4},
                DocumentStatus.PROCESSED,
                null,
                uploadedAt,
                updatedAt,
                null,
                3L
        );

        DocumentJpaEntity entity = mapper.toEntity(document);
        Document roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(document.getId());
        assertThat(roundTripped.getFileName()).isEqualTo(document.getFileName());
        assertThat(roundTripped.getContentType()).isEqualTo(document.getContentType());
        assertThat(roundTripped.getFileSize()).isEqualTo(document.getFileSize());
        assertThat(roundTripped.getFileData()).isEqualTo(document.getFileData());
        assertThat(roundTripped.getStatus()).isEqualTo(document.getStatus());
        assertThat(roundTripped.getErrorMessage()).isNull();
        assertThat(roundTripped.getUploadedAt()).isEqualTo(document.getUploadedAt());
        assertThat(roundTripped.getUpdatedAt()).isEqualTo(document.getUpdatedAt());
        assertThat(roundTripped.getDeletedAt()).isNull();
        assertThat(roundTripped.getVersion()).isEqualTo(document.getVersion());
    }

    @Test
    void roundTrip_errorDocument_preservesErrorMessage() {
        Instant uploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Document document = new Document(
                UUID.randomUUID(),
                "empty.txt",
                "text/plain",
                0L,
                new byte[0],
                DocumentStatus.ERROR,
                "Nenhum conteúdo extraído do documento",
                uploadedAt,
                uploadedAt,
                null,
                1L
        );

        Document roundTripped = mapper.toDomain(mapper.toEntity(document));

        assertThat(roundTripped.getStatus()).isEqualTo(DocumentStatus.ERROR);
        assertThat(roundTripped.getErrorMessage()).isEqualTo("Nenhum conteúdo extraído do documento");
    }

    @Test
    void roundTrip_deletedDocument_preservesDeletedAt() {
        Instant uploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant deletedAt = uploadedAt.plusSeconds(120);
        Document document = new Document(
                UUID.randomUUID(),
                "old.txt",
                "text/plain",
                50L,
                new byte[]{9},
                DocumentStatus.DELETED,
                null,
                uploadedAt,
                deletedAt,
                deletedAt,
                7L
        );

        Document roundTripped = mapper.toDomain(mapper.toEntity(document));

        assertThat(roundTripped.getStatus()).isEqualTo(DocumentStatus.DELETED);
        assertThat(roundTripped.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(roundTripped.getVersion()).isEqualTo(7L);
    }

    @Test
    void roundTrip_documentChunk_preservesAllFieldsIncludingEmbedding() {
        float[] embedding = new float[1536];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = i * 0.001f;
        }
        DocumentChunk chunk = new DocumentChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                "chunk text content",
                embedding,
                Instant.now().truncatedTo(ChronoUnit.MICROS)
        );

        DocumentChunk roundTripped = mapper.toDomain(mapper.toEntity(chunk));

        assertThat(roundTripped.getId()).isEqualTo(chunk.getId());
        assertThat(roundTripped.getDocumentId()).isEqualTo(chunk.getDocumentId());
        assertThat(roundTripped.getChunkIndex()).isEqualTo(chunk.getChunkIndex());
        assertThat(roundTripped.getChunkText()).isEqualTo(chunk.getChunkText());
        assertThat(roundTripped.getEmbedding()).isEqualTo(chunk.getEmbedding());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(chunk.getCreatedAt());
    }
}
