package com.helpdesk.rag.domain.service;

import com.helpdesk.rag.domain.exception.DocumentValidationException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates an uploaded file against RF-11: extension is the primary signal, size is a
 * hard threshold, and content-type is a secondary sanity check — a missing or generic
 * ({@code application/octet-stream}) content-type is accepted, and rejection only
 * happens when a present content-type explicitly contradicts the type expected for the
 * (already-valid) extension.
 */
public class FileValidationService {

    public static final long MAX_FILE_SIZE_BYTES = 10_485_760L;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "txt");

    private static final Map<String, String> EXPECTED_CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "txt", "text/plain"
    );

    private static final String OCTET_STREAM = "application/octet-stream";

    public void validate(String fileName, String contentType, long fileSize) {
        String extension = extractExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DocumentValidationException("Unsupported file extension: " + fileName);
        }
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new DocumentValidationException("File size " + fileSize + " bytes exceeds the 10MB limit");
        }
        if (isContradictoryContentType(contentType, extension)) {
            throw new DocumentValidationException(
                    "Content-type '" + contentType + "' contradicts extension ." + extension);
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isContradictoryContentType(String contentType, String extension) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).trim();
        if (normalized.equals(OCTET_STREAM)) {
            return false;
        }
        String expected = EXPECTED_CONTENT_TYPES.get(extension);
        return !normalized.equals(expected);
    }
}
