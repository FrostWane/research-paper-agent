package com.frostwane.paperagent.agent.retrieval;

import com.frostwane.paperagent.parse.PaperChunk;
import com.frostwane.paperagent.parse.PaperChunkRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class KeywordRetrievalChannel implements RetrievalChannel {

    private final PaperChunkRepository chunkRepository;

    public KeywordRetrievalChannel(PaperChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public String name() {
        return "keyword";
    }

    @Override
    public String label() {
        return "关键词";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean supports(RetrievalRequest request) {
        return request.query() != null && !request.query().isBlank();
    }

    @Override
    public List<RetrievalCandidate> retrieve(RetrievalRequest request) {
        List<PaperChunk> chunks = request.libraryScope()
            ? chunkRepository.findByOwnerIdOrderByPaperUpdatedAtDesc(request.owner().getId())
            : chunkRepository.findByPaperIdOrderByPageNumberAscChunkIndexAsc(request.paper().getId());
        if (chunks.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = tokenize(request.query());
        return chunks.stream()
            .map(chunk -> toCandidate(chunk, score(chunk.getContent(), tokens)))
            .filter(candidate -> candidate.score() > 0)
            .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed())
            .limit(request.candidateLimit())
            .toList();
    }

    private RetrievalCandidate toCandidate(PaperChunk chunk, double score) {
        return new RetrievalCandidate(
            chunk.getId(),
            chunk.getPaper().getId(),
            chunk.getPaper().getTitle(),
            chunk.getPageNumber(),
            chunk.getChunkIndex(),
            chunk.getContent(),
            score,
            name()
        );
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
}
