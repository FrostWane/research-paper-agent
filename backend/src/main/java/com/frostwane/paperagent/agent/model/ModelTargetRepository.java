package com.frostwane.paperagent.agent.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelTargetRepository extends JpaRepository<ModelTarget, Long> {
    List<ModelTarget> findAllByOrderByPriorityAscIdAsc();

    List<ModelTarget> findAllByEnabledTrueOrderByPriorityAscIdAsc();

    Optional<ModelTarget> findByCodeIgnoreCase(String code);
}
