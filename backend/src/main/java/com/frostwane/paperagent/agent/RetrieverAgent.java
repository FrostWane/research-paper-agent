package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.parse.PaperChunk;
import com.frostwane.paperagent.parse.PaperChunkRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RetrieverAgent {

    private final PaperChunkRepository chunkRepository;

    public RetrieverAgent(PaperChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public List<SourceResponse> retrieve(Paper paper, String question, boolean useRag) {
        if (!useRag) {
            return List.of();
        }
        List<PaperChunk> chunks = chunkRepository.findByPaperIdOrderByPageNumberAscChunkIndexAsc(paper.getId());
        if (chunks.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = tokenize(question);
        return chunks.stream()
            .map(chunk -> new ScoredChunk(chunk, score(chunk.getContent(), tokens)))
            .filter(item -> item.score > 0)
            .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
            .limit(5)
            .map(item -> new SourceResponse(
                paper.getId(),
                paper.getTitle(),
                item.chunk.getPageNumber(),
                compact(item.chunk.getContent(), 520)
            ))
            .toList();
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9\\u4e00-\\u9fa5]+"))
            .map(String::trim)
            .filter(token -> token.length() >= 2)
            .collect(Collectors.toSet());
    }

    private int score(String content, Set<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (lower.contains(token)) {
                score += Math.min(token.length(), 8);
            }
        }
        return score;
    }

    private String compact(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    private record ScoredChunk(PaperChunk chunk, int score) {
    }
}
