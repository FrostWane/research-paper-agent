package com.frostwane.paperagent.agent.retrieval;

public record RetrievalCandidate(
    Long chunkId,
    Long paperId,
    String title,
    int pageNumber,
    int chunkIndex,
    String content,
    double score,
    String channelName
) {
}
