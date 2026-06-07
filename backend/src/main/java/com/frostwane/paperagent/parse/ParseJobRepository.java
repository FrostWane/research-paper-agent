package com.frostwane.paperagent.parse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParseJobRepository extends JpaRepository<ParseJob, Long> {
    long countByOwnerId(Long ownerId);
    long countByOwnerIdAndStatus(Long ownerId, String status);
    Optional<ParseJob> findTopByOwnerIdAndPaperIdOrderByStartedAtDesc(Long ownerId, Long paperId);
}
