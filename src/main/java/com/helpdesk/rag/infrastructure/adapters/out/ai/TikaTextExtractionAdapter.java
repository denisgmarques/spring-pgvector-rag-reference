package com.helpdesk.rag.infrastructure.adapters.out.ai;

import com.helpdesk.rag.application.ports.out.TextExtractionPort;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Implements {@link TextExtractionPort} via Apache Tika's {@link AutoDetectParser},
 * which handles both PDF and TXT content through the same API (RF-02).
 */
@Component
public class TikaTextExtractionAdapter implements TextExtractionPort {

    @Override
    public String extractText(byte[] content, String contentType) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            parser.parse(stream, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (IOException | SAXException | TikaException e) {
            throw new IllegalStateException("Failed to extract text from document", e);
        }
    }
}
