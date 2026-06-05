package com.frostwane.paperagent.agent.retrieval;

import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.user.User;

public record RetrievalRequest(
    Paper paper,
    User owner,
    String query,
    boolean libraryScope,
    int candidateLimit
) {
}
