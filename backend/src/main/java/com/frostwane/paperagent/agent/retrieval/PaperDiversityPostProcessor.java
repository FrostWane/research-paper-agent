package com.frostwane.paperagent.agent.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaperDiversityPostProcessor implements RetrievalPostProcessor {

    @Override
    public String name() {
        return "paper-diversity";
    }

    @Override
    public String label() {
        return "多样性";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(RetrievalProcessingContext context) {
        return context.request().libraryScope();
    }

    @Override
    public List<RetrievalCandidate> process(RetrievalProcessingContext context, List<RetrievalCandidate> candidates) {
        if (candidates.size() <= context.resultLimit()) {
            return candidates;
        }

        int maxPerPaperFirstPass = Math.max(1, Math.min(2, context.resultLimit() / 2));
        Map<Long, Integer> paperCounts = new HashMap<>();
        List<RetrievalCandidate> selected = new ArrayList<>(context.resultLimit());

        for (RetrievalCandidate candidate : candidates) {
            int count = paperCounts.getOrDefault(candidate.paperId(), 0);
            if (count >= maxPerPaperFirstPass) {
                continue;
            }
            selected.add(candidate);
            paperCounts.put(candidate.paperId(), count + 1);
            if (selected.size() >= context.resultLimit()) {
                return selected;
            }
        }

        for (RetrievalCandidate candidate : candidates) {
            if (selected.contains(candidate)) {
                continue;
            }
            selected.add(candidate);
            if (selected.size() >= context.resultLimit()) {
                break;
            }
        }
        return selected;
    }
}
