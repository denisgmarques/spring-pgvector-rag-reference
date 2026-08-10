package com.helpdesk.rag.infrastructure.adapters.in.web;

import com.helpdesk.rag.domain.exception.DocumentProcessingConflictException;
import com.helpdesk.rag.domain.exception.DocumentValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice exercising {@link GlobalExceptionHandler} in isolation: a throwing stub
 * controller stands in for the real endpoints (T22/T23, not yet implemented in this
 * phase) so each mapped exception type can be asserted against the documented
 * status/body shape from openapi.yaml (RF-04, RF-07, RF-11, RF-12, CT-01, CT-03, CT-04).
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingStubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void documentValidationException_mapsTo400WithValidationErrorBody() throws Exception {
        mockMvc.perform(get("/stub/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.error", equalTo("Bad Request")))
                .andExpect(jsonPath("$.message", equalTo("invalid extension: .exe")))
                .andExpect(jsonPath("$.path", equalTo("/stub/validation")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void documentProcessingConflictException_mapsTo409WithProcessingMessage() throws Exception {
        mockMvc.perform(get("/stub/processing-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.error", equalTo("Conflict")))
                .andExpect(jsonPath("$.message", equalTo("Document is currently PROCESSING and cannot be updated")))
                .andExpect(jsonPath("$.path", equalTo("/stub/processing-conflict")));
    }

    @Test
    void objectOptimisticLockingFailureException_mapsTo409WithStaleVersionMessage() throws Exception {
        mockMvc.perform(get("/stub/stale-version"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.error", equalTo("Conflict")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("stale")))
                .andExpect(jsonPath("$.path", equalTo("/stub/stale-version")));
    }

    @Test
    void staleVersionConflictMessage_isDistinctFromProcessingConflictMessage() throws Exception {
        String processingMessage = mockMvc.perform(get("/stub/processing-conflict"))
                .andReturn().getResponse().getContentAsString();
        String staleVersionMessage = mockMvc.perform(get("/stub/stale-version"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(processingMessage).isNotEqualTo(staleVersionMessage);
    }

    @Test
    void noSuchElementException_mapsTo404WithNotFoundBody() throws Exception {
        mockMvc.perform(get("/stub/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.error", equalTo("Not Found")))
                .andExpect(jsonPath("$.message", equalTo("Document not found: missing-id")))
                .andExpect(jsonPath("$.path", equalTo("/stub/not-found")));
    }

    @RestController
    static class ThrowingStubController {

        @GetMapping("/stub/validation")
        void validation() {
            throw new DocumentValidationException("invalid extension: .exe");
        }

        @GetMapping("/stub/processing-conflict")
        void processingConflict() {
            throw new DocumentProcessingConflictException("Document is currently PROCESSING and cannot be updated");
        }

        @GetMapping("/stub/stale-version")
        void staleVersion() {
            throw new ObjectOptimisticLockingFailureException("Document", "some-id");
        }

        @GetMapping("/stub/not-found")
        void notFound() {
            throw new NoSuchElementException("Document not found: missing-id");
        }
    }
}
