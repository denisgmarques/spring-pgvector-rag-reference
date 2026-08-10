package com.helpdesk.rag.domain.exception;

public class DocumentProcessingConflictException extends RuntimeException {

    public DocumentProcessingConflictException(String message) {
        super(message);
    }
}
