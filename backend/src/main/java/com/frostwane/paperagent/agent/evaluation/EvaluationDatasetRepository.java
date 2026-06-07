package com.frostwane.paperagent.agent.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationDatasetRepository extends JpaRepository<EvaluationDataset, Long> {
    Optional<EvaluationDataset> findByCodeIgnoreCase(String code);
    List<EvaluationDataset> findAllByOrderByUpdatedAtDescIdDesc();
}
