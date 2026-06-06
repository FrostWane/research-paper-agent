package com.frostwane.paperagent.agent.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ChannelFusionPostProcessor implements RetrievalPostProcessor {

    @Override
    public String name() {
        return "channel-fusion";
    }

    @Override
    public String label() {
        return "融合";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(RetrievalProcessingContext context) {
        return context.channelResults().stream().anyMatch(result -> !result.candidates().isEmpty());
    }

    @Override
    public List<RetrievalCandidate> process(RetrievalProcessingContext context, List<RetrievalCandidate> candidates) {
        Map<String, AggregatedCandidate> fused = new LinkedHashMap<>();
        for (RetrievalChannelResult result : context.channelResults()) {
            if (!"SUCCESS".equals(result.status()) || result.candidates().isEmpty()) {
                continue;
            }
            double maxScore = result.candidates().stream()
                .mapToDouble(RetrievalCandidate::score)
                .max()
                .orElse(1.0d);
            int size = result.candidates().size();
            for (int i = 0; i < size; i++) {
                RetrievalCandidate candidate = result.candidates().get(i);
                double normalizedScore = maxScore <= 0 ? 0 : candidate.score() / maxScore;
                double rankScore = (size - i) / (double) Math.max(size, 1);
                double contribution = channelWeight(context, result.name()) * (0.65d * rankScore + 0.35d * normalizedScore);
                fused.computeIfAbsent(candidateKey(candidate), ignored -> new AggregatedCandidate(candidate))
                    .add(contribution, result.name());
            }
        }

        int fusionLimit = Math.max(context.resultLimit() * 3, context.resultLimit());
        return fused.values().stream()
            .sorted(Comparator.comparingDouble(AggregatedCandidate::score).reversed()
                .thenComparing(item -> item.candidate().title())
                .thenComparingInt(item -> item.candidate().pageNumber()))
            .limit(fusionLimit)
            .map(AggregatedCandidate::toCandidate)
            .toList();
    }

    private double channelWeight(RetrievalProcessingContext context, String channelName) {
        return switch (channelName) {
            case "vector" -> context.request().settings().vectorWeight();
            case "keyword" -> context.request().settings().keywordWeight();
            default -> 0.65d;
        };
    }

    private String candidateKey(RetrievalCandidate candidate) {
        if (candidate.chunkId() != null) {
            return "chunk:" + candidate.chunkId();
        }
        return "paper:" + candidate.paperId() + ":page:" + candidate.pageNumber() + ":index:" + candidate.chunkIndex();
    }

    private static class AggregatedCandidate {
        private final RetrievalCandidate candidate;
        private final List<String> channels = new ArrayList<>();
        private double score;

        private AggregatedCandidate(RetrievalCandidate candidate) {
            this.candidate = candidate;
        }

        private void add(double contribution, String channelName) {
            score += contribution;
            if (!channels.contains(channelName)) {
                channels.add(channelName);
            }
        }

        private double score() {
            return score;
        }

        private RetrievalCandidate candidate() {
            return candidate;
        }

        private RetrievalCandidate toCandidate() {
            return new RetrievalCandidate(
                candidate.chunkId(),
                candidate.paperId(),
                candidate.title(),
                candidate.pageNumber(),
                candidate.chunkIndex(),
                candidate.content(),
                score,
                channels.stream().collect(Collectors.joining("+"))
            );
        }
    }
}
