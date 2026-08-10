package com.helpdesk.rag.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Splits extracted document text into chunks (RF-02): by paragraph when the text
 * contains more than one paragraph, otherwise falling back to fixed 1000-character
 * blocks with a 200-character overlap (RNF-07). Blank/whitespace-only input yields no
 * chunks, feeding the zero-chunk -> ERROR path (RF-02/RF-04).
 */
public class ChunkingService {

    private static final int FIXED_CHUNK_SIZE = 1000;
    private static final int OVERLAP_SIZE = 200;
    private static final int STRIDE = FIXED_CHUNK_SIZE - OVERLAP_SIZE;
    private static final String PARAGRAPH_DELIMITER = "\\n\\s*\\n+";

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.strip();
        List<String> paragraphs = Arrays.stream(trimmed.split(PARAGRAPH_DELIMITER))
                .map(String::strip)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toList());

        if (paragraphs.size() > 1) {
            return paragraphs;
        }
        return fixedWindowChunks(trimmed);
    }

    private List<String> fixedWindowChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + FIXED_CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start += STRIDE;
        }
        return chunks;
    }
}
