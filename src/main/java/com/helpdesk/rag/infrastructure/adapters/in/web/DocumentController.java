package com.helpdesk.rag.infrastructure.adapters.in.web;

import com.helpdesk.rag.application.ports.in.DeleteDocumentUseCase;
import com.helpdesk.rag.application.ports.in.DeleteDocumentUseCase.DeleteDocumentCommand;
import com.helpdesk.rag.application.ports.in.ListDocumentsUseCase;
import com.helpdesk.rag.application.ports.in.ListDocumentsUseCase.DocumentPageResult;
import com.helpdesk.rag.application.ports.in.ListDocumentsUseCase.ListDocumentsQuery;
import com.helpdesk.rag.application.ports.in.UpdateDocumentUseCase;
import com.helpdesk.rag.application.ports.in.UpdateDocumentUseCase.UpdateDocumentCommand;
import com.helpdesk.rag.application.ports.in.UpdateDocumentUseCase.UpdateDocumentResult;
import com.helpdesk.rag.application.ports.in.UploadDocumentUseCase;
import com.helpdesk.rag.application.ports.in.UploadDocumentUseCase.UploadDocumentCommand;
import com.helpdesk.rag.application.ports.in.UploadDocumentUseCase.UploadDocumentResult;
import com.helpdesk.rag.infrastructure.adapters.in.web.dto.DocumentListResponse;
import com.helpdesk.rag.infrastructure.adapters.in.web.dto.DocumentSummaryResponse;
import com.helpdesk.rag.infrastructure.adapters.in.web.dto.UpdateDocumentResponse;
import com.helpdesk.rag.infrastructure.adapters.in.web.dto.UploadDocumentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements CT-01..CT-04 exactly per openapi.yaml: upload (RF-01/RF-11), infinite-scroll
 * listing (RF-05), update (RF-06/RF-07/RF-11) and soft delete (RF-07/RF-08). Error-path
 * status codes (400/404/409) are produced by {@link GlobalExceptionHandler} translating
 * the exceptions thrown by the underlying use cases.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final ListDocumentsUseCase listDocumentsUseCase;
    private final UpdateDocumentUseCase updateDocumentUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    public DocumentController(UploadDocumentUseCase uploadDocumentUseCase,
                               ListDocumentsUseCase listDocumentsUseCase,
                               UpdateDocumentUseCase updateDocumentUseCase,
                               DeleteDocumentUseCase deleteDocumentUseCase) {
        this.uploadDocumentUseCase = uploadDocumentUseCase;
        this.listDocumentsUseCase = listDocumentsUseCase;
        this.updateDocumentUseCase = updateDocumentUseCase;
        this.deleteDocumentUseCase = deleteDocumentUseCase;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadDocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        UploadDocumentCommand command = new UploadDocumentCommand(
                file.getOriginalFilename(), file.getContentType(), file.getSize(), readBytes(file));
        UploadDocumentResult result = uploadDocumentUseCase.upload(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UploadDocumentResponse(result.documentId(), result.fileName(), result.status(),
                        result.errorMessage(), result.uploadedAt(), result.updatedAt(), result.version()));
    }

    @GetMapping
    public ResponseEntity<DocumentListResponse> list(@RequestParam(value = "cursor", required = false) String cursor) {
        DocumentPageResult result = listDocumentsUseCase.list(new ListDocumentsQuery(cursor));
        List<DocumentSummaryResponse> items = result.items().stream()
                .map(item -> new DocumentSummaryResponse(item.id(), item.fileName(), item.status(), item.uploadedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(new DocumentListResponse(items, result.nextCursor()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UpdateDocumentResponse> update(@PathVariable UUID id,
                                                          @RequestParam("file") MultipartFile file) {
        UpdateDocumentCommand command = new UpdateDocumentCommand(
                id, file.getOriginalFilename(), file.getContentType(), file.getSize(), readBytes(file));
        UpdateDocumentResult result = updateDocumentUseCase.update(command);
        return ResponseEntity.ok(new UpdateDocumentResponse(result.documentId(), result.fileName(), result.status(),
                result.errorMessage(), result.uploadedAt(), result.updatedAt(), result.version()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteDocumentUseCase.delete(new DeleteDocumentCommand(id));
        return ResponseEntity.noContent().build();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
