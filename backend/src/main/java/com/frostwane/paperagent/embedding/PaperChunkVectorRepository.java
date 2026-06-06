package com.frostwane.paperagent.embedding;

import com.frostwane.paperagent.parse.PaperChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class PaperChunkVectorRepository {

    private static final RowMapper<VectorSearchResult> SEARCH_RESULT_ROW_MAPPER = (rs, rowNum) -> new VectorSearchResult(
        rs.getLong("id"),
        rs.getLong("paper_id"),
        rs.getString("title"),
        rs.getInt("page_number"),
        rs.getInt("chunk_index"),
        rs.getString("content"),
        rs.getDouble("similarity")
    );

    private final JdbcTemplate jdbcTemplate;

    public PaperChunkVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void updateEmbeddings(List<PaperChunk> chunks, List<float[]> embeddings, String embeddingId) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings must have the same size");
        }
        List<Object[]> batchArgs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            batchArgs.add(new Object[] {toPgVector(embeddings.get(i)), embeddingId, chunks.get(i).getId()});
        }
        jdbcTemplate.batchUpdate("UPDATE paper_chunks SET embedding = ?::vector, embedding_id = ? WHERE id = ?", batchArgs);
    }

    public List<VectorSearchResult> searchByEmbedding(Long paperId, float[] queryEmbedding, int limit) {
        String vector = toPgVector(queryEmbedding);
        return jdbcTemplate.query(
            """
                SELECT chunk.id, paper.id AS paper_id, paper.title, chunk.page_number, chunk.chunk_index, chunk.content,
                       1 - (chunk.embedding <=> ?::vector) AS similarity
                FROM paper_chunks chunk
                JOIN papers paper ON paper.id = chunk.paper_id
                WHERE chunk.paper_id = ? AND chunk.embedding IS NOT NULL AND chunk.enabled = TRUE
                ORDER BY chunk.embedding <=> ?::vector
                LIMIT ?
                """,
            SEARCH_RESULT_ROW_MAPPER,
            vector,
            paperId,
            vector,
            limit
        );
    }

    public List<VectorSearchResult> searchOwnerByEmbedding(Long ownerId, float[] queryEmbedding, int limit) {
        String vector = toPgVector(queryEmbedding);
        return jdbcTemplate.query(
            """
                SELECT chunk.id, paper.id AS paper_id, paper.title, chunk.page_number, chunk.chunk_index, chunk.content,
                       1 - (chunk.embedding <=> ?::vector) AS similarity
                FROM paper_chunks chunk
                JOIN papers paper ON paper.id = chunk.paper_id
                WHERE paper.owner_id = ? AND chunk.embedding IS NOT NULL AND chunk.enabled = TRUE
                ORDER BY chunk.embedding <=> ?::vector
                LIMIT ?
                """,
            SEARCH_RESULT_ROW_MAPPER,
            vector,
            ownerId,
            vector,
            limit
        );
    }

    public long countIndexedByPaperId(Long paperId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM paper_chunks WHERE paper_id = ? AND embedding IS NOT NULL",
            Long.class,
            paperId
        );
        return count == null ? 0 : count;
    }

    private String toPgVector(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 10);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            float value = Float.isFinite(vector[i]) ? vector[i] : 0.0f;
            builder.append(value);
        }
        builder.append(']');
        return builder.toString();
    }

    public record VectorSearchResult(
        Long id,
        Long paperId,
        String title,
        int pageNumber,
        int chunkIndex,
        String content,
        double similarity
    ) {
    }
}
