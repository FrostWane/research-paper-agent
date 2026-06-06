package com.frostwane.paperagent.agent.intent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntentRouteRepository extends JpaRepository<IntentRoute, Long> {
    List<IntentRoute> findAllByOrderBySortOrderAscIdAsc();

    List<IntentRoute> findByEnabledTrueOrderBySortOrderAscIdAsc();

    Optional<IntentRoute> findByIntentCodeIgnoreCase(String intentCode);
}
