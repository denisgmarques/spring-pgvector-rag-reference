package com.helpdesk.rag.application.ports.out;

public interface TextExtractionPort {

    String extractText(byte[] content, String contentType);
}
