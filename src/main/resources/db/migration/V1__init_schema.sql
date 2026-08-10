CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id             UUID PRIMARY KEY,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100),
    file_size      BIGINT NOT NULL,
    file_data      BYTEA NOT NULL,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'ERROR', 'DELETED')),
    error_message  TEXT,
    uploaded_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,
    deleted_at     TIMESTAMP,
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE document_chunks (
    id           UUID PRIMARY KEY,
    document_id  UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index  INT NOT NULL,
    chunk_text   TEXT NOT NULL,
    embedding    VECTOR(1536) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_status_uploaded_at ON documents (status, uploaded_at DESC, id DESC);

CREATE INDEX idx_document_chunks_embedding_hnsw ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
