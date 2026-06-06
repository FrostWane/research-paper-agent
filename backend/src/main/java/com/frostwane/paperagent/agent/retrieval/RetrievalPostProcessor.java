package com.frostwane.paperagent.agent.retrieval;

import java.util.List;

public interface RetrievalPostProcessor {

    String name();

    String label();

    int order();

    boolean supports(RetrievalProcessingContext context);

    List<RetrievalCandidate> process(RetrievalProcessingContext context, List<RetrievalCandidate> candidates);
}
