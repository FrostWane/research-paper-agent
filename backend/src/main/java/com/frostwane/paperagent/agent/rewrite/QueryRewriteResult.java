package com.frostwane.paperagent.agent.rewrite;

import java.util.List;

public record QueryRewriteResult(
    String rewrittenQuery,
    List<String> subQuestions,
    String modelName
) {
}
