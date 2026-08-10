package com.helpdesk.rag.infrastructure.adapters.in.web.dto;

import com.helpdesk.rag.domain.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryResponse(UUID id, String fileName, DocumentStatus status, Instant uploadedAt) {
}
