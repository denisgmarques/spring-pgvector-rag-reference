package com.helpdesk.rag.infrastructure.adapters.in.web;

import com.helpdesk.rag.application.ports.in.DeleteDocumentUseCase;
import com.helpdesk.rag.application.ports.in.ListDocumentsUseCase;
import com.helpdesk.rag.application.ports.in.UpdateDocumentUseCase;
import com.helpdesk.rag.application.ports.in.UploadDocumentUseCase;
import com.helpdesk.rag.domain.DocumentStatus;
import com.helpdesk.rag.domain.exception.DocumentProcessingConflictException;
import com.helpdesk.rag.domain.exception.DocumentValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice with mocked use cases exercising every status code documented for
 * CT-01..CT-04 in openapi.yaml: 201 (upload), 400 (validation), 200 with items/nextCursor
 * (list), 200/PROCESSING (update), 409 (PROCESSING conflict / update-delete), 404
 * (unknown id) and 204 (delete).
 */
class DocumentControllerTest {

    private UploadDocumentUseCase uploadDocumentUseCase;
    private ListDocumentsUseCase listDocumentsUseCase;
    private UpdateDocumentUseCase updateDocumentUseCase;
    private DeleteDocumentUseCase deleteDocumentUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        uploadDocumentUseCase = mock(UploadDocumentUseCase.class);
        listDocumentsUseCase = mock(ListDocumentsUseCase.class);
        updateDocumentUseCase = mock(UpdateDocumentUseCase.class);
        deleteDocumentUseCase = mock(DeleteDocumentUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DocumentController(uploadDocumentUseCase, listDocumentsUseCase, updateDocumentUseCase, deleteDocumentUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
                .build();
    }

    @Test
    void upload_validFile_returns201WithPendingStatus() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant uploadedAt = Instant.now();
        when(uploadDocumentUseCase.upload(any())).thenReturn(
                new UploadDocumentUseCase.UploadDocumentResult(
                        documentId, "manual.txt", DocumentStatus.PENDING, null, uploadedAt, null, 0L));

        MockMultipartFile file = new MockMultipartFile("file", "manual.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(documentId.toString())))
                .andExpect(jsonPath("$.fileName", equalTo("manual.txt")))
                .andExpect(jsonPath("$.status", equalTo("PENDING")))
                .andExpect(jsonPath("$.uploadedAt", equalTo(uploadedAt.toString())))
                .andExpect(jsonPath("$.version", equalTo(0)))
                .andExpect(jsonPath("$.errorMessage", nullValue()));
    }

    @Test
    void upload_invalidExtension_returns400() throws Exception {
        when(uploadDocumentUseCase.upload(any()))
                .thenThrow(new DocumentValidationException("Unsupported file extension: malware.exe"));

        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.error", equalTo("Bad Request")))
                .andExpect(jsonPath("$.message", equalTo("Unsupported file extension: malware.exe")))
                .andExpect(jsonPath("$.path", equalTo("/api/documents")));
    }

    @Test
    void upload_oversizedFile_returns400() throws Exception {
        when(uploadDocumentUseCase.upload(any()))
                .thenThrow(new DocumentValidationException("File size 10485761 bytes exceeds the 10MB limit"));

        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", "x".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    void list_returnsItemsAndNextCursor() throws Exception {
        UUID id1 = UUID.randomUUID();
        Instant now = Instant.now();
        when(listDocumentsUseCase.list(any())).thenReturn(
                new ListDocumentsUseCase.DocumentPageResult(
                        List.of(new ListDocumentsUseCase.DocumentSummary(id1, "doc1.pdf", DocumentStatus.PROCESSED, now)),
                        "next-cursor-token"));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(1)))
                .andExpect(jsonPath("$.items[0].id", equalTo(id1.toString())))
                .andExpect(jsonPath("$.items[0].fileName", equalTo("doc1.pdf")))
                .andExpect(jsonPath("$.items[0].status", equalTo("PROCESSED")))
                .andExpect(jsonPath("$.nextCursor", equalTo("next-cursor-token")));
    }

    @Test
    void list_withCursor_forwardsCursorToUseCase() throws Exception {
        when(listDocumentsUseCase.list(any())).thenReturn(
                new ListDocumentsUseCase.DocumentPageResult(List.of(), null));

        mockMvc.perform(get("/api/documents").param("cursor", "opaque-cursor-1"))
                .andExpect(status().isOk());

        verify(listDocumentsUseCase).list(new ListDocumentsUseCase.ListDocumentsQuery("opaque-cursor-1"));
    }

    @Test
    void list_lastBatch_returnsNullNextCursor() throws Exception {
        when(listDocumentsUseCase.list(any())).thenReturn(
                new ListDocumentsUseCase.DocumentPageResult(List.of(), null));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", equalTo(0)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void update_validFile_returns200WithProcessingStatus() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant uploadedAt = Instant.now().minusSeconds(60);
        Instant updatedAt = Instant.now();
        when(updateDocumentUseCase.update(any())).thenReturn(
                new UpdateDocumentUseCase.UpdateDocumentResult(
                        documentId, "manual-v2.txt", DocumentStatus.PROCESSING, null, uploadedAt, updatedAt, 1L));

        MockMultipartFile file = new MockMultipartFile("file", "manual-v2.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", documentId).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(documentId.toString())))
                .andExpect(jsonPath("$.fileName", equalTo("manual-v2.txt")))
                .andExpect(jsonPath("$.status", equalTo("PROCESSING")))
                .andExpect(jsonPath("$.uploadedAt", equalTo(uploadedAt.toString())))
                .andExpect(jsonPath("$.updatedAt", equalTo(updatedAt.toString())))
                .andExpect(jsonPath("$.version", equalTo(1)));
    }

    @Test
    void update_documentProcessing_returns409() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(updateDocumentUseCase.update(any()))
                .thenThrow(new DocumentProcessingConflictException(
                        "Document " + documentId + " is currently PROCESSING and cannot be updated"));

        MockMultipartFile file = new MockMultipartFile("file", "manual-v2.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", documentId).file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.error", equalTo("Conflict")));
    }

    @Test
    void update_unknownDocument_returns404() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(updateDocumentUseCase.update(any()))
                .thenThrow(new NoSuchElementException("Document not found: " + documentId));

        MockMultipartFile file = new MockMultipartFile("file", "manual-v2.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/{id}", documentId).file(file))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)));
    }

    @Test
    void delete_valid_returns204() throws Exception {
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isNoContent());

        verify(deleteDocumentUseCase).delete(new DeleteDocumentUseCase.DeleteDocumentCommand(documentId));
    }

    @Test
    void delete_documentProcessing_returns409() throws Exception {
        UUID documentId = UUID.randomUUID();
        doThrow(new DocumentProcessingConflictException(
                "Document " + documentId + " is currently PROCESSING and cannot be deleted"))
                .when(deleteDocumentUseCase).delete(any());

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)));
    }

    @Test
    void delete_unknownDocument_returns404() throws Exception {
        UUID documentId = UUID.randomUUID();
        doThrow(new NoSuchElementException("Document not found: " + documentId))
                .when(deleteDocumentUseCase).delete(any());

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isNotFound());
    }
}
