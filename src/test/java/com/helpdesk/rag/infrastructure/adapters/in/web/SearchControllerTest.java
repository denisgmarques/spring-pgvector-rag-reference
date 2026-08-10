package com.helpdesk.rag.infrastructure.adapters.in.web;

import com.helpdesk.rag.application.ports.in.SearchRagUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice with a mocked {@link SearchRagUseCase} covering CT-05/RF-10: a valid
 * question returns the JSON array in the exact order/shape produced by the use case, and
 * a blank/missing question is rejected with 400 via Bean Validation ({@code @NotBlank}
 * on {@code SearchRequest}).
 */
class SearchControllerTest {

    private SearchRagUseCase searchRagUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        searchRagUseCase = mock(SearchRagUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchRagUseCase))
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void search_validQuestion_returnsResultsInUseCaseOrder() throws Exception {
        UUID documentId1 = UUID.randomUUID();
        UUID documentId2 = UUID.randomUUID();
        when(searchRagUseCase.search(any())).thenReturn(List.of(
                new SearchRagUseCase.SearchResult(documentId1, "manual.pdf", "chunk de maior score", 92.5),
                new SearchRagUseCase.SearchResult(documentId2, "faq.txt", "chunk de menor score", 41.0)
        ));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Como resetar a senha?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", equalTo(2)))
                .andExpect(jsonPath("$[0].documentId", equalTo(documentId1.toString())))
                .andExpect(jsonPath("$[0].documentName", equalTo("manual.pdf")))
                .andExpect(jsonPath("$[0].chunkText", equalTo("chunk de maior score")))
                .andExpect(jsonPath("$[0].score", equalTo(92.5)))
                .andExpect(jsonPath("$[1].documentId", equalTo(documentId2.toString())))
                .andExpect(jsonPath("$[1].documentName", equalTo("faq.txt")))
                .andExpect(jsonPath("$[1].score", equalTo(41.0)));

        verify(searchRagUseCase).search(new SearchRagUseCase.SearchQuery("Como resetar a senha?"));
    }

    @Test
    void search_noMatches_returnsEmptyArray() throws Exception {
        when(searchRagUseCase.search(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"pergunta sem correspondencia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", equalTo(0)));
    }

    @Test
    void search_blankQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_missingQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
