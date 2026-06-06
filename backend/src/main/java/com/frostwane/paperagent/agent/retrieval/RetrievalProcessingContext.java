package com.frostwane.paperagent.agent.retrieval;

import java.util.List;

public record RetrievalProcessingContext(
    RetrievalRequest request,
    List<RetrievalChannelResult> channelResults,
    int resultLimit
) {
}
