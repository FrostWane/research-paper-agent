package com.frostwane.paperagent.parse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaperChunkRepository extends JpaRepository<PaperChunk, Long> {
    @Query("""
        select chunk
        from PaperChunk chunk
        join fetch chunk.paper paper
        where paper.id = :paperId
          and chunk.enabled = true
        order by chunk.pageNumber asc, chunk.chunkIndex asc
        """)
    List<PaperChunk> findByPaperIdOrderByPageNumberAscChunkIndexAsc(@Param("paperId") Long paperId);

    @Query("""
        select chunk
        from PaperChunk chunk
        join fetch chunk.paper paper
        where paper.owner.id = :ownerId
          and chunk.enabled = true
        order by paper.updatedAt desc, chunk.pageNumber asc, chunk.chunkIndex asc
        """)
    List<PaperChunk> findByOwnerIdOrderByPaperUpdatedAtDesc(@Param("ownerId") Long ownerId);

    @Query("""
        select count(chunk)
        from PaperChunk chunk
        where chunk.paper.owner.id = :ownerId
        """)
    long countByOwnerId(@Param("ownerId") Long ownerId);

    long countByPaperId(Long paperId);
    void deleteByPaperId(Long paperId);
}
