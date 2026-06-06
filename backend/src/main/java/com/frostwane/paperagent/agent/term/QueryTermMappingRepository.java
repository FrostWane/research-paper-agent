package com.frostwane.paperagent.agent.term;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QueryTermMappingRepository extends JpaRepository<QueryTermMapping, Long> {

    List<QueryTermMapping> findAllByOrderByUpdatedAtDesc();

    List<QueryTermMapping> findByEnabledTrueOrderByTermAsc();

    Optional<QueryTermMapping> findByTermIgnoreCase(String term);
}
