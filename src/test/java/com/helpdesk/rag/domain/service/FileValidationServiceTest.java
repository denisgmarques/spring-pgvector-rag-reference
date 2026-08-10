package com.helpdesk.rag.domain.service;

import com.helpdesk.rag.domain.exception.DocumentValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationServiceTest {

    private final FileValidationService service = new FileValidationService();

    @Test
    void validPdf_withMatchingContentType_isAccepted() {
        assertThatCode(() -> service.validate("report.pdf", "application/pdf", 1_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void validTxt_withMatchingContentType_isAccepted() {
        assertThatCode(() -> service.validate("notes.txt", "text/plain", 1_000L))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "document.doc",
            "document.png",
            "document",
            "document.",
    })
    void invalidExtension_isRejected(String fileName) {
        assertThatThrownBy(() -> service.validate(fileName, "text/plain", 1_000L))
                .isInstanceOf(DocumentValidationException.class);
    }

    @Test
    void sizeExactlyAtLimit_isAccepted() {
        assertThatCode(() -> service.validate("file.txt", "text/plain", FileValidationService.MAX_FILE_SIZE_BYTES))
                .doesNotThrowAnyException();
    }

    @Test
    void sizeOneByteOverLimit_isRejected() {
        assertThatThrownBy(() -> service.validate("file.txt", "text/plain", FileValidationService.MAX_FILE_SIZE_BYTES + 1))
                .isInstanceOf(DocumentValidationException.class);
    }

    @Test
    void missingContentType_isAccepted() {
        assertThatCode(() -> service.validate("file.txt", null, 1_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void blankContentType_isAccepted() {
        assertThatCode(() -> service.validate("file.pdf", "  ", 1_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void octetStreamContentType_isAccepted() {
        assertThatCode(() -> service.validate("file.pdf", "application/octet-stream", 1_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void contradictoryContentType_txtDeclaredAsImagePng_isRejected() {
        assertThatThrownBy(() -> service.validate("file.txt", "image/png", 1_000L))
                .isInstanceOf(DocumentValidationException.class);
    }

    @Test
    void contradictoryContentType_pdfDeclaredAsTextPlain_isRejected() {
        assertThatThrownBy(() -> service.validate("file.pdf", "text/plain", 1_000L))
                .isInstanceOf(DocumentValidationException.class);
    }
}
