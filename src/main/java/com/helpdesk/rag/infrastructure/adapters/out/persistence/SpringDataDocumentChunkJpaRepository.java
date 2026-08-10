package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataDocumentChunkJpaRepository extends JpaRepository<DocumentChunkJpaEntity, UUID> {
}
