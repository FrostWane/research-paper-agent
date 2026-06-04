package com.frostwane.paperagent.parse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperChunkRepository extends JpaRepository<PaperChunk, Long> {
    List<PaperChunk> findByPaperIdOrderByPageNumberAscChunkIndexAsc(Long paperId);
    long countByPaperId(Long paperId);
    void deleteByPaperId(Long paperId);
}
