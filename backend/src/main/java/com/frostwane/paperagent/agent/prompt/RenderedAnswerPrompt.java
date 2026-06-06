package com.frostwane.paperagent.agent.prompt;

public record RenderedAnswerPrompt(
    String templateCode,
    String systemPrompt,
    String userPrompt
) {
}
