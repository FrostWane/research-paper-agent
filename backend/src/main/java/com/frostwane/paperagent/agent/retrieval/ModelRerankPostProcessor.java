package com.frostwane.paperagent.agent.retrieval;

import com.frostwane.paperagent.agent.settings.RagSettingsSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ModelRerankPostProcessor implements RetrievalPostProcessor {

    private final ModelRerankAgent rerankAgent;

    public ModelRerankPostProcessor(ModelRerankAgent rerankAgent) {
        this.rerankAgent = rerankAgent;
    }

    @Override
    public String name() {
        return "model-rerank";
    }

    @Override
    public String label() {
        return "模型重排";
    }

    @Override
    public int order() {
        return 18;
    }

    @Override
    public boolean supports(RetrievalProcessingContext context) {
        RagSettingsSnapshot settings = context.request().settings();
        return settings.rerankModelEnabled()
            && context.request().query() != null
            && !context.request().query().isBlank();
    }

    @Override
    public List<RetrievalCandidate> process(RetrievalProcessingContext context, List<RetrievalCandidate> candidates) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        int windowSize = Math.min(candidates.size(), context.request().settings().rerankModelMaxCandidates());
        List<RetrievalCandidate> head = candidates.subList(0, windowSize);
        List<Integer> order = rerankAgent.rerank(context.request().query(), head);
        List<RetrievalCandidate> reranked = new ArrayList<>(candidates.size());
        for (Integer index : order) {
            if (index >= 0 && index < head.size()) {
                reranked.add(head.get(index));
            }
        }
        if (reranked.size() < head.size()) {
            for (RetrievalCandidate candidate : head) {
                if (!reranked.contains(candidate)) {
                    reranked.add(candidate);
                }
            }
        }
        reranked.addAll(candidates.subList(windowSize, candidates.size()));
        return reranked;
    }
}
