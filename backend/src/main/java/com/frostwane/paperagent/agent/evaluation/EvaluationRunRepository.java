package com.frostwane.paperagent.agent.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long>, JpaSpecificationExecutor<EvaluationRun> {
}
