package com.frostwane.paperagent.agent.retrieval;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResultLimitPostProcessor implements RetrievalPostProcessor {

    @Override
    public String name() {
        return "result-limit";
    }

    @Override
    public String label() {
        return "截断";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public boolean supports(RetrievalProcessingContext context) {
        return true;
    }

    @Override
    public List<RetrievalCandidate> process(RetrievalProcessingContext context, List<RetrievalCandidate> candidates) {
        if (candidates.size() <= context.resultLimit()) {
            return candidates;
        }
        return candidates.stream().limit(context.resultLimit()).toList();
    }
}
