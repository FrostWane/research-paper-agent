package com.frostwane.paperagent.agent.prompt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnswerPromptTemplateRepository extends JpaRepository<AnswerPromptTemplate, Long> {
    List<AnswerPromptTemplate> findAllByOrderBySortOrderAscIdAsc();

    Optional<AnswerPromptTemplate> findByCodeIgnoreCase(String code);

    Optional<AnswerPromptTemplate> findFirstByEnabledTrueAndDefaultTemplateTrueOrderBySortOrderAscIdAsc();

    Optional<AnswerPromptTemplate> findFirstByEnabledTrueOrderBySortOrderAscIdAsc();

    @Modifying
    @Query("update AnswerPromptTemplate t set t.defaultTemplate = false where t.id <> :id")
    void clearDefaultExcept(@Param("id") Long id);
}
