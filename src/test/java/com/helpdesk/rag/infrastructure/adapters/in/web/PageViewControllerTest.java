package com.helpdesk.rag.infrastructure.adapters.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Verifies PageViewController resolves the shell pages required by UI-01/UI-02/UI-03:
 * GET /, /documents, /search and /upload all return HTTP 200 and resolve the expected
 * Thymeleaf view, and documents.html carries the status-badge/edit/delete markup that
 * UI-02 requires (populated dynamically by documents.js since data loads via AJAX).
 */
@WebMvcTest(PageViewController.class)
class PageViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void home_returns200AndResolvesDocumentsView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("documents"));
    }

    @Test
    void documents_returns200AndResolvesDocumentsView() throws Exception {
        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(view().name("documents"))
                .andExpect(content().string(containsString("doc-status-badge")))
                .andExpect(content().string(containsString("doc-edit-btn")))
                .andExpect(content().string(containsString("doc-delete-btn")));
    }

    @Test
    void search_returns200AndResolvesSearchView() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(view().name("search"));
    }

    @Test
    void upload_returns200AndResolvesUploadView() throws Exception {
        mockMvc.perform(get("/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"));
    }
}
