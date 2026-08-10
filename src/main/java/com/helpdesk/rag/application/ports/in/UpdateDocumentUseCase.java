package com.helpdesk.rag.application.ports.in;

import com.helpdesk.rag.domain.DocumentStatus;

import java.util.UUID;

public interface UpdateDocumentUseCase {

    UpdateDocumentResult update(UpdateDocumentCommand command);

    record UpdateDocumentCommand(UUID documentId, String fileName, String contentType, long fileSize, byte[] fileData) {
    }

    record UpdateDocumentResult(UUID documentId, DocumentStatus status) {
    }
}
