package com.frostwane.paperagent.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaperFileRepository extends JpaRepository<PaperFile, Long> {
    Optional<PaperFile> findByIdAndOwnerId(Long id, Long ownerId);
}
