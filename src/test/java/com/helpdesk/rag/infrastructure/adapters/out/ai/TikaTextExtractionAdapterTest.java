package com.helpdesk.rag.infrastructure.adapters.out.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TikaTextExtractionAdapterTest {

    private final TikaTextExtractionAdapter adapter = new TikaTextExtractionAdapter();

    @Test
    void extractsNonEmptyText_fromPdfFixture() throws IOException {
        byte[] content = readFixture("sample.pdf");

        String text = adapter.extractText(content, "application/pdf");

        assertThat(text).isNotBlank();
        assertThat(text).contains("Hello RAG World");
    }

    @Test
    void extractsNonEmptyText_fromTxtFixture() throws IOException {
        byte[] content = readFixture("sample.txt");

        String text = adapter.extractText(content, "text/plain");

        assertThat(text).isNotBlank();
        assertThat(text).contains("Hello RAG World");
    }

    @Test
    void blankTxtFixture_returnsEmptyOrWhitespaceOnlyString() throws IOException {
        byte[] content = readFixture("blank.txt");

        String text = adapter.extractText(content, "text/plain");

        assertThat(text.isBlank()).isTrue();
    }

    private byte[] readFixture(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/" + name)) {
            return stream.readAllBytes();
        }
    }
}
