package com.frostwane.paperagent.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaperFileRepository extends JpaRepository<PaperFile, Long> {
    Optional<PaperFile> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);

    @Query("select coalesce(sum(file.size), 0) from PaperFile file where file.owner.id = :ownerId")
    long sumSizeByOwnerId(@Param("ownerId") Long ownerId);
}
