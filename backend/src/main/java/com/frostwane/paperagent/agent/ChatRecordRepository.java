package com.frostwane.paperagent.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {
    List<ChatRecord> findByOwnerIdAndPaperIdOrderByCreatedAtAsc(Long ownerId, Long paperId);
    List<ChatRecord> findByOwnerIdAndPaperIsNullOrderByCreatedAtAsc(Long ownerId);
}
