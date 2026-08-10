package com.helpdesk.rag.infrastructure.adapters.in.web.dto;

import com.helpdesk.rag.domain.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record UploadDocumentResponse(UUID id,
                                      String fileName,
                                      DocumentStatus status,
                                      String errorMessage,
                                      Instant uploadedAt,
                                      Instant updatedAt,
                                      long version) {
}
