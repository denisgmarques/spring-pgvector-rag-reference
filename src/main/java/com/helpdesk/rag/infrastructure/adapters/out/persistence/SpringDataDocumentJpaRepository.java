package com.helpdesk.rag.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataDocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {

    @Query(value = "SELECT * FROM documents WHERE status <> 'DELETED' "
            + "ORDER BY uploaded_at DESC, id DESC LIMIT :limit", nativeQuery = true)
    List<DocumentJpaEntity> findFirstBatch(@Param("limit") int limit);

    @Query(value = "SELECT * FROM documents WHERE status <> 'DELETED' "
            + "AND (uploaded_at, id) < (:cursorUploadedAt, :cursorId) "
            + "ORDER BY uploaded_at DESC, id DESC LIMIT :limit", nativeQuery = true)
    List<DocumentJpaEntity> findNextBatch(@Param("cursorUploadedAt") Instant cursorUploadedAt,
                                           @Param("cursorId") UUID cursorId,
                                           @Param("limit") int limit);
}
