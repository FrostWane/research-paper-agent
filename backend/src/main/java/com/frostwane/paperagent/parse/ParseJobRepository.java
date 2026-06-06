package com.frostwane.paperagent.parse;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParseJobRepository extends JpaRepository<ParseJob, Long> {
    long countByOwnerId(Long ownerId);
    long countByOwnerIdAndStatus(Long ownerId, String status);
}
