package com.frostwane.paperagent.agent.term;

import java.util.List;

public record QueryTermExpansion(
    Long id,
    String term,
    List<String> expansions
) {
}
