package com.helpdesk.rag.infrastructure.adapters.in.web.dto;

import java.util.UUID;

public record SearchResultResponse(UUID documentId, String documentName, String chunkText, double score) {
}
