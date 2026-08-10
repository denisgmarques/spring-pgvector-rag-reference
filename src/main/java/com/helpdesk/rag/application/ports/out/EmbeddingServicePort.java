package com.helpdesk.rag.application.ports.out;

public interface EmbeddingServicePort {

    /**
     * @return an embedding vector of exactly 1536 dimensions (CT-06).
     */
    float[] embed(String text);
}
