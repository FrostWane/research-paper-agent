package com.frostwane.paperagent.agent.retrieval;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class MultiChannelRetrievalEngine {

    private final List<RetrievalChannel> channels;

    public MultiChannelRetrievalEngine(List<RetrievalChannel> channels) {
        this.channels = channels.stream()
            .sorted(Comparator.comparingInt(RetrievalChannel::priority))
            .toList();
    }

    public RetrievalResult retrieve(RetrievalRequest request, int resultLimit) {
        List<RetrievalChannel> enabledChannels = channels.stream()
            .filter(channel -> channel.supports(request))
            .toList();
        if (enabledChannels.isEmpty()) {
            return RetrievalResult.empty();
        }

        List<RetrievalChannelResult> channelResults = enabledChannels.stream()
            .map(channel -> CompletableFuture.supplyAsync(() -> runChannel(channel, request)))
            .map(CompletableFuture::join)
            .toList();

        List<SourceResponse> sources = rerank(channelResults, resultLimit).stream()
            .map(candidate -> new SourceResponse(
                candidate.paperId(),
                candidate.title(),
                candidate.pageNumber(),
                compact(candidate.content(), 520)
            ))
            .toList();

        return new RetrievalResult(
            sources,
            channelResults.stream().map(RetrievalChannelTrace::from).toList()
        );
    }

    private RetrievalChannelResult runChannel(RetrievalChannel channel, RetrievalRequest request) {
        Instant started = Instant.now();
        try {
            List<RetrievalCandidate> candidates = channel.retrieve(request);
            return RetrievalChannelResult.success(channel, candidates, elapsedMs(started));
        } catch (Exception ex) {
            return RetrievalChannelResult.failure(channel, elapsedMs(started), ex);
        }
    }

    private List<RetrievalCandidate> rerank(List<RetrievalChannelResult> channelResults, int resultLimit) {
        Map<String, AggregatedCandidate> candidates = new LinkedHashMap<>();
        for (RetrievalChannelResult result : channelResults) {
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
                String key = candidateKey(candidate);
                double normalizedScore = maxScore <= 0 ? 0 : candidate.score() / maxScore;
                double rankScore = (size - i) / (double) Math.max(size, 1);
                double contribution = channelWeight(result.name()) * (0.65d * rankScore + 0.35d * normalizedScore);
                candidates.computeIfAbsent(key, ignored -> new AggregatedCandidate(candidate))
                    .add(contribution, result.name());
            }
        }

        return candidates.values().stream()
            .sorted(Comparator.comparingDouble(AggregatedCandidate::score).reversed()
                .thenComparing(item -> item.candidate().title())
                .thenComparingInt(item -> item.candidate().pageNumber()))
            .limit(resultLimit)
            .map(AggregatedCandidate::toCandidate)
            .toList();
    }

    private double channelWeight(String channelName) {
        return switch (channelName) {
            case "vector" -> 1.0d;
            case "keyword" -> 0.78d;
            default -> 0.65d;
        };
    }

    private String candidateKey(RetrievalCandidate candidate) {
        if (candidate.chunkId() != null) {
            return "chunk:" + candidate.chunkId();
        }
        return "paper:" + candidate.paperId() + ":page:" + candidate.pageNumber() + ":index:" + candidate.chunkIndex();
    }

    private int elapsedMs(Instant started) {
        return Math.toIntExact(Math.min(Duration.between(started, Instant.now()).toMillis(), Integer.MAX_VALUE));
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
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
