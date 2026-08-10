package com.helpdesk.rag.application.ports.in;

import java.util.UUID;

public interface DeleteDocumentUseCase {

    void delete(DeleteDocumentCommand command);

    record DeleteDocumentCommand(UUID documentId) {
    }
}
