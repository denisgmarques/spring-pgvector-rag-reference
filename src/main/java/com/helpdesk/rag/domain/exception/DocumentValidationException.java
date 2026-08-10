package com.helpdesk.rag.domain.exception;

public class DocumentValidationException extends RuntimeException {

    public DocumentValidationException(String message) {
        super(message);
    }
}
