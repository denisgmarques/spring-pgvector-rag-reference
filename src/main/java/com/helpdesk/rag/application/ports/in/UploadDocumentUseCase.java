package com.helpdesk.rag.application.ports.in;

import com.helpdesk.rag.domain.DocumentStatus;

import java.util.UUID;

public interface UploadDocumentUseCase {

    UploadDocumentResult upload(UploadDocumentCommand command);

    record UploadDocumentCommand(String fileName, String contentType, long fileSize, byte[] fileData) {
    }

    record UploadDocumentResult(UUID documentId, DocumentStatus status) {
    }
}
