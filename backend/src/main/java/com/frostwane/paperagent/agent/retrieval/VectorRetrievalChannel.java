package com.frostwane.paperagent.agent.retrieval;

import com.frostwane.paperagent.embedding.EmbeddingService;
import com.frostwane.paperagent.embedding.PaperChunkVectorRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorRetrievalChannel implements RetrievalChannel {

    private final EmbeddingService embeddingService;
    private final PaperChunkVectorRepository vectorRepository;

    public VectorRetrievalChannel(EmbeddingService embeddingService, PaperChunkVectorRepository vectorRepository) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    @Override
    public String name() {
        return "vector";
    }

    @Override
    public String label() {
        return "向量";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(RetrievalRequest request) {
        return request.query() != null && !request.query().isBlank();
    }

    @Override
    public List<RetrievalCandidate> retrieve(RetrievalRequest request) {
        float[] queryEmbedding = embeddingService.embed(request.query());
        return (request.libraryScope()
            ? vectorRepository.searchOwnerByEmbedding(request.owner().getId(), queryEmbedding, request.candidateLimit())
            : vectorRepository.searchByEmbedding(request.paper().getId(), queryEmbedding, request.candidateLimit()))
            .stream()
            .map(result -> new RetrievalCandidate(
                result.id(),
                result.paperId(),
                result.title(),
                result.pageNumber(),
                result.chunkIndex(),
                result.content(),
                result.similarity(),
                name()
            ))
            .toList();
    }
}
