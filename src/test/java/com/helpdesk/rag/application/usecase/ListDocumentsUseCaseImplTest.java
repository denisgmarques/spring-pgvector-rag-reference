package com.helpdesk.rag.application.usecase;

import com.helpdesk.rag.application.ports.in.ListDocumentsUseCase.DocumentPageResult;
import com.helpdesk.rag.application.ports.in.ListDocumentsUseCase.ListDocumentsQuery;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort.DocumentBatch;
import com.helpdesk.rag.domain.Document;
import com.helpdesk.rag.domain.DocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListDocumentsUseCaseImplTest {

    private DocumentRepositoryPort documentRepositoryPort;
    private ListDocumentsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        documentRepositoryPort = mock(DocumentRepositoryPort.class);
        useCase = new ListDocumentsUseCaseImpl(documentRepositoryPort);
    }

    private List<Document> canned(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Document.uploadNew(UUID.randomUUID(), "file" + i + ".txt", "text/plain", 10L, new byte[0], Instant.now()))
                .collect(Collectors.toList());
    }

    @Test
    void fullBatch_mapsToTwentyResults_withNonNullCursor() {
        when(documentRepositoryPort.findBatch(isNull(), eq(20))).thenReturn(new DocumentBatch(canned(20), "next-cursor-token"));

        DocumentPageResult result = useCase.list(new ListDocumentsQuery(null));

        assertThat(result.items()).hasSize(20);
        assertThat(result.nextCursor()).isEqualTo("next-cursor-token");
    }

    @Test
    void partialBatch_mapsToNullCursor() {
        when(documentRepositoryPort.findBatch(isNull(), eq(20))).thenReturn(new DocumentBatch(canned(5), null));

        DocumentPageResult result = useCase.list(new ListDocumentsQuery(null));

        assertThat(result.items()).hasSize(5);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void inputCursor_isForwardedUnchangedToPort() {
        when(documentRepositoryPort.findBatch(anyString(), anyInt())).thenReturn(new DocumentBatch(canned(0), null));

        useCase.list(new ListDocumentsQuery("incoming-cursor"));

        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentRepositoryPort).findBatch(cursorCaptor.capture(), eq(20));
        assertThat(cursorCaptor.getValue()).isEqualTo("incoming-cursor");
    }

    @Test
    void summaryFields_mapCorrectly() {
        Document document = Document.uploadNew(UUID.randomUUID(), "report.pdf", "application/pdf", 10L, new byte[0], Instant.now());
        when(documentRepositoryPort.findBatch(isNull(), eq(20))).thenReturn(new DocumentBatch(List.of(document), null));

        DocumentPageResult result = useCase.list(new ListDocumentsQuery(null));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(document.getId());
        assertThat(result.items().get(0).fileName()).isEqualTo("report.pdf");
        assertThat(result.items().get(0).status()).isEqualTo(DocumentStatus.PENDING);
    }
}
