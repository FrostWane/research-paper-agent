package com.frostwane.paperagent.agent.sample;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SamplePromptRepository extends JpaRepository<SamplePrompt, Long> {

    List<SamplePrompt> findAllByOrderBySortOrderAscUpdatedAtDesc();

    List<SamplePrompt> findByEnabledTrueAndScopeOrderBySortOrderAscUpdatedAtDesc(String scope);
}
