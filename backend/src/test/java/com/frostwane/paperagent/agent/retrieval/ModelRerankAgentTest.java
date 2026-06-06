package com.frostwane.paperagent.agent.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRerankAgentTest {

    private final ModelRerankAgent agent = new ModelRerankAgent(null, new ObjectMapper());

    @Test
    void parsesZeroBasedJsonRankingAndAppendsMissingIndexes() {
        List<Integer> ranking = agent.parseRanking("{\"rankedIndexes\":[2,0]}", 4);

        assertThat(ranking).containsExactly(2, 0, 1, 3);
    }

    @Test
    void acceptsOneBasedModelOutput() {
        List<Integer> ranking = agent.parseRanking("[3,1,2]", 3);

        assertThat(ranking).containsExactly(2, 0, 1);
    }

    @Test
    void fallsBackToOriginalOrderWhenOutputIsUnusable() {
        List<Integer> ranking = agent.parseRanking("无法判断", 3);

        assertThat(ranking).containsExactly(0, 1, 2);
    }
}
