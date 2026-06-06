package com.frostwane.paperagent.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionSummaryRepository extends JpaRepository<ChatSessionSummary, Long> {
    Optional<ChatSessionSummary> findFirstBySessionIdOrderByIdDesc(Long sessionId);
}
