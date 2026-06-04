package com.frostwane.paperagent.agent;

import com.frostwane.paperagent.agent.dto.AgentDtos.SourceResponse;
import com.frostwane.paperagent.embedding.EmbeddingService;
import com.frostwane.paperagent.embedding.PaperChunkVectorRepository;
import com.frostwane.paperagent.embedding.PaperChunkVectorRepository.VectorSearchResult;
import com.frostwane.paperagent.paper.Paper;
import com.frostwane.paperagent.parse.PaperChunk;
import com.frostwane.paperagent.parse.PaperChunkRepository;
import com.frostwane.paperagent.user.User;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RetrieverAgent {

    private static final int RESULT_LIMIT = 5;

    private final PaperChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final PaperChunkVectorRepository vectorRepository;

    public RetrieverAgent(
        PaperChunkRepository chunkRepository,
        EmbeddingService embeddingService,
        PaperChunkVectorRepository vectorRepository
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    public List<SourceResponse> retrieve(Paper paper, String question, boolean useRag) {
        if (!useRag) {
            return List.of();
        }
        List<SourceResponse> vectorSources = retrieveByVector(paper, question);
        if (!vectorSources.isEmpty()) {
            return vectorSources;
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
            .limit(RESULT_LIMIT)
            .map(item -> toSource(item.chunk))
            .toList();
    }

    public List<SourceResponse> retrieveLibrary(User owner, String question, boolean useRag) {
        if (!useRag) {
            return List.of();
        }
        List<SourceResponse> vectorSources = retrieveLibraryByVector(owner, question);
        if (!vectorSources.isEmpty()) {
            return vectorSources;
        }
        List<PaperChunk> chunks = chunkRepository.findByOwnerIdOrderByPaperUpdatedAtDesc(owner.getId());
        if (chunks.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = tokenize(question);
        return chunks.stream()
            .map(chunk -> new ScoredChunk(chunk, score(chunk.getContent(), tokens)))
            .filter(item -> item.score > 0)
            .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
            .limit(RESULT_LIMIT)
            .map(item -> toSource(item.chunk))
            .toList();
    }

    private List<SourceResponse> retrieveByVector(Paper paper, String question) {
        try {
            float[] queryEmbedding = embeddingService.embed(question);
            return vectorRepository.searchByEmbedding(paper.getId(), queryEmbedding, RESULT_LIMIT).stream()
                .map(this::toSource)
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SourceResponse> retrieveLibraryByVector(User owner, String question) {
        try {
            float[] queryEmbedding = embeddingService.embed(question);
            return vectorRepository.searchOwnerByEmbedding(owner.getId(), queryEmbedding, RESULT_LIMIT).stream()
                .map(this::toSource)
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private SourceResponse toSource(VectorSearchResult result) {
        return new SourceResponse(
            result.paperId(),
            result.title(),
            result.pageNumber(),
            compact(result.content(), 520)
        );
    }

    private SourceResponse toSource(PaperChunk chunk) {
        Paper paper = chunk.getPaper();
        return new SourceResponse(
            paper.getId(),
            paper.getTitle(),
            chunk.getPageNumber(),
            compact(chunk.getContent(), 520)
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

    private String compact(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    private record ScoredChunk(PaperChunk chunk, int score) {
    }
}
