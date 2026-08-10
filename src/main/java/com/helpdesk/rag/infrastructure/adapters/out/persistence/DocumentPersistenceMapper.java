package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentChunk;
import org.springframework.stereotype.Component;

@Component
public class DocumentPersistenceMapper {

    public DocumentJpaEntity toEntity(Document document) {
        return new DocumentJpaEntity(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getFileData(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getUploadedAt(),
                document.getUpdatedAt(),
                document.getDeletedAt(),
                document.getVersion()
        );
    }

    public Document toDomain(DocumentJpaEntity entity) {
        return new Document(
                entity.getId(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getFileData(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getUploadedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                entity.getVersion()
        );
    }

    public DocumentChunkJpaEntity toEntity(DocumentChunk chunk) {
        return new DocumentChunkJpaEntity(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getChunkText(),
                chunk.getEmbedding(),
                chunk.getCreatedAt()
        );
    }

    public DocumentChunk toDomain(DocumentChunkJpaEntity entity) {
        return new DocumentChunk(
                entity.getId(),
                entity.getDocumentId(),
                entity.getChunkIndex(),
                entity.getChunkText(),
                entity.getEmbedding(),
                entity.getCreatedAt()
        );
    }
}
