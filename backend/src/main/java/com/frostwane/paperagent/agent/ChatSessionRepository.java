package com.frostwane.paperagent.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByIdAndOwnerId(Long id, Long ownerId);
    List<ChatSession> findByOwnerIdAndArchivedFalseOrderByUpdatedAtDesc(Long ownerId);
    List<ChatSession> findByOwnerIdAndPaperIdAndArchivedFalseOrderByUpdatedAtDesc(Long ownerId, Long paperId);
    List<ChatSession> findByOwnerIdAndPaperIsNullAndArchivedFalseOrderByUpdatedAtDesc(Long ownerId);
    List<ChatSession> findByOwnerIdAndPaperIdAndArchivedFalseOrderByUpdatedAtDesc(Long ownerId, Long paperId, Pageable pageable);
    List<ChatSession> findByOwnerIdAndPaperIsNullAndArchivedFalseOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);
}
