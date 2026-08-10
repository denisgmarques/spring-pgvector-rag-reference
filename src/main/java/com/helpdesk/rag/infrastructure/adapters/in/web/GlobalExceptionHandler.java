package com.helpdesk.rag.infrastructure.adapters.in.web;

import com.helpdesk.rag.domain.exception.DocumentProcessingConflictException;
import com.helpdesk.rag.domain.exception.DocumentValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Translates domain/persistence exceptions into the HTTP error shapes documented in
 * openapi.yaml (ValidationErrorResponse/ConflictErrorResponse/NotFoundErrorResponse).
 * The PROCESSING conflict (RF-07) and the stale optimistic-lock conflict (RF-12) both
 * map to 409 but carry distinct messages so clients can tell the two apart.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(DocumentValidationException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(DocumentProcessingConflictException.class)
    public ResponseEntity<ErrorResponse> handleProcessingConflict(DocumentProcessingConflictException ex,
                                                                    HttpServletRequest request) {
        return errorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleStaleVersion(ObjectOptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        return errorResponse(HttpStatus.CONFLICT,
                "The document was modified by another request; the supplied version is stale", request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
    }
}
