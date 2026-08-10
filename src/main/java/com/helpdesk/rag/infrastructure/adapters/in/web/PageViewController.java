package com.helpdesk.rag.infrastructure.adapters.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Thymeleaf shell pages (UI-01/UI-02/UI-03): server-rendered page shells only
 * — document/search data is loaded client-side via jQuery AJAX against
 * {@link DocumentController}/{@link SearchController}, never pre-rendered here.
 */
@Controller
public class PageViewController {

    @GetMapping("/")
    public String home() {
        return "documents";
    }

    @GetMapping("/documents")
    public String documents() {
        return "documents";
    }

    @GetMapping("/search")
    public String search() {
        return "search";
    }

    @GetMapping("/upload")
    public String upload() {
        return "upload";
    }
}
