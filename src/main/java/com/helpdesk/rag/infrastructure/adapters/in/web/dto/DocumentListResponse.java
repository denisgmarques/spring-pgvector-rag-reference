package com.helpdesk.rag.infrastructure.adapters.in.web.dto;

import java.util.List;

public record DocumentListResponse(List<DocumentSummaryResponse> items, String nextCursor) {
}
