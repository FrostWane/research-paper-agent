package com.frostwane.paperagent.agent.retrieval;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;

@Service
public class MultiChannelRetrievalEngine {

    private final List<RetrievalChannel> channels;
    private final List<RetrievalPostProcessor> postProcessors;

    public MultiChannelRetrievalEngine(List<RetrievalChannel> channels, List<RetrievalPostProcessor> postProcessors) {
        this.channels = channels.stream()
            .sorted(Comparator.comparingInt(RetrievalChannel::priority))
            .toList();
        this.postProcessors = postProcessors.stream()
            .sorted(Comparator.comparingInt(RetrievalPostProcessor::order))
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

        ProcessedRetrieval processed = runPostProcessors(new RetrievalProcessingContext(request, channelResults, resultLimit));
        List<SourceResponse> sources = processed.candidates().stream()
            .map(candidate -> new SourceResponse(
                candidate.paperId(),
                candidate.title(),
                candidate.pageNumber(),
                compact(candidate.content(), request.settings().sourceExcerptChars())
            ))
            .toList();

        return new RetrievalResult(
            sources,
            channelResults.stream().map(RetrievalChannelTrace::from).toList(),
            processed.processorTraces()
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

    private ProcessedRetrieval runPostProcessors(RetrievalProcessingContext context) {
        List<RetrievalCandidate> candidates = List.of();
        List<RetrievalProcessorTrace> traces = new ArrayList<>();
        for (RetrievalPostProcessor processor : postProcessors) {
            if (!processor.supports(context)) {
                continue;
            }
            int inputCount = candidates.size();
            Instant started = Instant.now();
            try {
                candidates = processor.process(context, candidates);
                traces.add(RetrievalProcessorTrace.success(processor, inputCount, candidates.size(), elapsedMs(started)));
            } catch (Exception ex) {
                traces.add(RetrievalProcessorTrace.failure(processor, inputCount, candidates.size(), elapsedMs(started), ex));
            }
        }
        return new ProcessedRetrieval(candidates, traces);
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

    private record ProcessedRetrieval(
        List<RetrievalCandidate> candidates,
        List<RetrievalProcessorTrace> processorTraces
    ) {
    }
}
