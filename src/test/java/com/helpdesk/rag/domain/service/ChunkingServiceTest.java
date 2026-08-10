package com.helpdesk.rag.domain.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService();

    @Test
    void multiParagraphText_splitsByParagraph() {
        String text = "First paragraph line one.\nFirst paragraph line two."
                + "\n\n"
                + "Second paragraph."
                + "\n\n"
                + "Third paragraph.";

        List<String> chunks = service.chunk(text);

        assertThat(chunks).containsExactly(
                "First paragraph line one.\nFirst paragraph line two.",
                "Second paragraph.",
                "Third paragraph."
        );
    }

    @Test
    void singleBlockText2500Chars_fallsBackToFixedChunksWithExactBoundariesAndOverlap() {
        String text = "a".repeat(2500);

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(1000);
        assertThat(chunks.get(1)).hasSize(1000);
        assertThat(chunks.get(2)).hasSize(900);

        // Reconstruct expected boundaries: stride = 1000 - 200 = 800.
        String expectedChunk0 = text.substring(0, 1000);
        String expectedChunk1 = text.substring(800, 1800);
        String expectedChunk2 = text.substring(1600, 2500);
        assertThat(chunks).containsExactly(expectedChunk0, expectedChunk1, expectedChunk2);

        // Exact 200-character overlap between consecutive chunks.
        assertThat(chunks.get(0).substring(800)).isEqualTo(chunks.get(1).substring(0, 200));
        assertThat(chunks.get(1).substring(800)).isEqualTo(chunks.get(2).substring(0, 200));
    }

    @Test
    void singleBlockTextWithDistinctCharacters_overlapContainsIdenticalText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            builder.append((char) ('A' + (i % 26)));
        }
        String text = builder.toString();

        List<String> chunks = service.chunk(text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).substring(800, 1000)).isEqualTo(chunks.get(1).substring(0, 200));
        assertThat(chunks.get(1).substring(800, 1000)).isEqualTo(chunks.get(2).substring(0, 200));
    }

    @Test
    void emptyString_returnsEmptyList() {
        assertThat(service.chunk("")).isEmpty();
    }

    @Test
    void whitespaceOnlyInput_returnsEmptyList() {
        assertThat(service.chunk("   \n\n   \t  ")).isEmpty();
    }

    @Test
    void nullInput_returnsEmptyList() {
        assertThat(service.chunk(null)).isEmpty();
    }

    @Test
    void singleShortParagraph_returnsSingleChunk() {
        assertThat(service.chunk("Just one short paragraph.")).containsExactly("Just one short paragraph.");
    }
}
