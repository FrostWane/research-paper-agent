package com.frostwane.paperagent.agent.evaluation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationCaseResultRepository extends JpaRepository<EvaluationCaseResult, Long> {
    Page<EvaluationCaseResult> findByRunIdOrderByIdAsc(Long runId, Pageable pageable);
}
