package com.frostwane.paperagent.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {
    long countByOwnerId(Long ownerId);
    long countByOwnerIdAndPaperIsNull(Long ownerId);
    List<ChatRecord> findByOwnerIdAndPaperIdOrderByCreatedAtAsc(Long ownerId, Long paperId);
    List<ChatRecord> findByOwnerIdAndPaperIsNullOrderByCreatedAtAsc(Long ownerId);
    List<ChatRecord> findByOwnerIdAndSessionIdOrderByCreatedAtAsc(Long ownerId, Long sessionId);
    List<ChatRecord> findByOwnerIdAndSessionIdAndIdGreaterThanOrderByCreatedAtAsc(Long ownerId, Long sessionId, Long id);
    List<ChatRecord> findByOwnerIdAndPaperIdOrderByCreatedAtDesc(Long ownerId, Long paperId, Pageable pageable);
    List<ChatRecord> findByOwnerIdAndPaperIsNullOrderByCreatedAtDesc(Long ownerId, Pageable pageable);
    List<ChatRecord> findByOwnerIdAndSessionIdOrderByCreatedAtDesc(Long ownerId, Long sessionId, Pageable pageable);
    Optional<ChatRecord> findByIdAndOwnerId(Long id, Long ownerId);
}
