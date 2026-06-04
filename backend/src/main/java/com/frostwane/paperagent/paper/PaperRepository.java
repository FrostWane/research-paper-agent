package com.frostwane.paperagent.paper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long>, JpaSpecificationExecutor<Paper> {
    Optional<Paper> findByIdAndOwnerId(Long id, Long ownerId);
}
