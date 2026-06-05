package com.frostwane.paperagent.agent.retrieval;

import java.util.List;

public interface RetrievalChannel {

    String name();

    String label();

    int priority();

    boolean supports(RetrievalRequest request);

    List<RetrievalCandidate> retrieve(RetrievalRequest request);
}
