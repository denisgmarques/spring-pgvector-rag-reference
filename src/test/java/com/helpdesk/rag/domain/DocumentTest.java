package com.helpdesk.rag.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    private Document newPendingDocument() {
        return Document.uploadNew(UUID.randomUUID(), "file.txt", "text/plain", 10L, new byte[]{1, 2, 3}, Instant.now());
    }

    @Test
    void validTransition_pendingToProcessingToProcessed_succeeds() {
        Document document = newPendingDocument();

        document.markProcessing();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);

        document.markProcessed();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSED);
        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void validTransition_processingToError_succeeds() {
        Document document = newPendingDocument();
        document.markProcessing();

        document.markError("boom");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.ERROR);
        assertThat(document.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void invalidTransition_pendingDirectlyToProcessed_throws() {
        Document document = newPendingDocument();

        assertThatThrownBy(document::markProcessed).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidTransition_pendingDirectlyToError_throws() {
        Document document = newPendingDocument();

        assertThatThrownBy(() -> document.markError("boom")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidTransition_processingToProcessingAgain_throws() {
        Document document = newPendingDocument();
        document.markProcessing();

        assertThatThrownBy(document::markProcessing).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reprocessing_processedBackToProcessing_succeeds() {
        Document document = newPendingDocument();
        document.markProcessing();
        document.markProcessed();

        document.markProcessing();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void reprocessing_errorBackToProcessing_succeeds() {
        Document document = newPendingDocument();
        document.markProcessing();
        document.markError("boom");

        document.markProcessing();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void markDeleted_setsStatusDeletedAndDeletedAt() {
        Document document = newPendingDocument();
        Instant now = Instant.now();

        document.markDeleted(now);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.DELETED);
        assertThat(document.getDeletedAt()).isEqualTo(now);
    }

    @Test
    void markDeleted_whileProcessing_throws() {
        Document document = newPendingDocument();
        document.markProcessing();

        assertThatThrownBy(() -> document.markDeleted(Instant.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markDeleted_alreadyDeleted_throws() {
        Document document = newPendingDocument();
        document.markDeleted(Instant.now());

        assertThatThrownBy(() -> document.markDeleted(Instant.now())).isInstanceOf(IllegalStateException.class);
    }
}
