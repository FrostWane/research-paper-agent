package com.frostwane.paperagent.agent.intent;

public record IntentRouteMatch(
    String intentCode,
    String label,
    String searchHint,
    String answerStrategy,
    String answerContract,
    String boundToolName,
    boolean comparisonRequested
) {
    public static IntentRouteMatch general() {
        return new IntentRouteMatch("GENERAL_QA", "通用问答", "", "EVIDENCE_GROUNDED_QA", "", null, false);
    }
}
