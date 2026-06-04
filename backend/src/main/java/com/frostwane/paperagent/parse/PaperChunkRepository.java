package com.frostwane.paperagent.parse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaperChunkRepository extends JpaRepository<PaperChunk, Long> {
    List<PaperChunk> findByPaperIdOrderByPageNumberAscChunkIndexAsc(Long paperId);

    @Query("""
        select chunk
        from PaperChunk chunk
        join fetch chunk.paper paper
        where paper.owner.id = :ownerId
        order by paper.updatedAt desc, chunk.pageNumber asc, chunk.chunkIndex asc
        """)
    List<PaperChunk> findByOwnerIdOrderByPaperUpdatedAtDesc(@Param("ownerId") Long ownerId);

    long countByPaperId(Long paperId);
    void deleteByPaperId(Long paperId);
}
