package com.frostwane.paperagent.agent.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EvaluationCaseRepository extends JpaRepository<EvaluationCase, Long>, JpaSpecificationExecutor<EvaluationCase> {
    long countByDatasetId(Long datasetId);
    long countByDatasetIdAndEnabledTrue(Long datasetId);
    List<EvaluationCase> findByDatasetIdAndEnabledTrueOrderByIdAsc(Long datasetId);
}
