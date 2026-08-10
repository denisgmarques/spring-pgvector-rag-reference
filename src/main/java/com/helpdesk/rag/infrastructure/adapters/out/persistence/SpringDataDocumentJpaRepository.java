package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataDocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {
}
