package com.helpdesk.rag.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor for post-commit document processing (Tika extraction, chunking,
 * embedding generation). Combined with @TransactionalEventListener(AFTER_COMMIT) on the
 * listener, this ensures the HTTP upload/update thread returns immediately instead of
 * blocking on extraction/embedding work (RNF-02).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "documentProcessingExecutor")
    public TaskExecutor documentProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-processing-");
        executor.initialize();
        return executor;
    }
}
