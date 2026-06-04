package com.frostwane.paperagent.embedding;

import com.frostwane.paperagent.parse.PaperChunk;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaperEmbeddingIndexer {

    private static final int BATCH_SIZE = 32;

    private final EmbeddingService embeddingService;
    private final PaperChunkVectorRepository vectorRepository;

    public PaperEmbeddingIndexer(EmbeddingService embeddingService, PaperChunkVectorRepository vectorRepository) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    public void index(List<PaperChunk> chunks) {
        for (int start = 0; start < chunks.size(); start += BATCH_SIZE) {
            int end = Math.min(chunks.size(), start + BATCH_SIZE);
            List<PaperChunk> batch = chunks.subList(start, end);
            List<float[]> embeddings = embeddingService.embedAll(batch.stream().map(PaperChunk::getContent).toList());
            vectorRepository.updateEmbeddings(batch, embeddings, embeddingService.providerId());
        }
    }

    public long indexedCount(Long paperId) {
        return vectorRepository.countIndexedByPaperId(paperId);
    }
}
