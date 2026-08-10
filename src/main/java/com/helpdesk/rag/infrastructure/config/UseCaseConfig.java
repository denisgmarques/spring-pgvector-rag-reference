package com.helpdesk.rag.infrastructure.config;

import com.helpdesk.rag.application.ports.out.DocumentRepositoryPort;
import com.helpdesk.rag.application.ports.out.EmbeddingServicePort;
import com.helpdesk.rag.application.usecase.DeleteDocumentUseCaseImpl;
import com.helpdesk.rag.application.usecase.ListDocumentsUseCaseImpl;
import com.helpdesk.rag.application.usecase.SearchRagUseCaseImpl;
import com.helpdesk.rag.application.usecase.UpdateDocumentUseCaseImpl;
import com.helpdesk.rag.application.usecase.UploadDocumentUseCaseImpl;
import com.helpdesk.rag.domain.service.ChunkingService;
import com.helpdesk.rag.domain.service.FileValidationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires domain services (RNF-01: framework-annotation-free) and use case
 * implementations (kept free of Spring annotations to preserve hexagonal layering) as
 * beans. All Spring-specific configuration for the application layer lives here rather
 * than on the domain/application classes themselves.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public FileValidationService fileValidationService() {
        return new FileValidationService();
    }

    @Bean
    public ChunkingService chunkingService() {
        return new ChunkingService();
    }

    @Bean
    public UploadDocumentUseCaseImpl uploadDocumentUseCase(FileValidationService fileValidationService,
                                                             DocumentRepositoryPort documentRepositoryPort,
                                                             ApplicationEventPublisher eventPublisher) {
        return new UploadDocumentUseCaseImpl(fileValidationService, documentRepositoryPort, eventPublisher);
    }

    @Bean
    public UpdateDocumentUseCaseImpl updateDocumentUseCase(FileValidationService fileValidationService,
                                                             DocumentRepositoryPort documentRepositoryPort,
                                                             ApplicationEventPublisher eventPublisher) {
        return new UpdateDocumentUseCaseImpl(fileValidationService, documentRepositoryPort, eventPublisher);
    }

    @Bean
    public DeleteDocumentUseCaseImpl deleteDocumentUseCase(DocumentRepositoryPort documentRepositoryPort) {
        return new DeleteDocumentUseCaseImpl(documentRepositoryPort);
    }

    @Bean
    public ListDocumentsUseCaseImpl listDocumentsUseCase(DocumentRepositoryPort documentRepositoryPort) {
        return new ListDocumentsUseCaseImpl(documentRepositoryPort);
    }

    @Bean
    public SearchRagUseCaseImpl searchRagUseCase(EmbeddingServicePort embeddingServicePort,
                                                  DocumentRepositoryPort documentRepositoryPort) {
        return new SearchRagUseCaseImpl(embeddingServicePort, documentRepositoryPort);
    }
}
