package com.frostwane.paperagent.paper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long>, JpaSpecificationExecutor<Paper> {
    Optional<Paper> findByIdAndOwnerId(Long id, Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Paper p where p.id = :id and p.owner.id = :ownerId")
    Optional<Paper> lockByIdAndOwnerId(@Param("id") Long id, @Param("ownerId") Long ownerId);
    long countByOwnerId(Long ownerId);
    long countByOwnerIdAndProcessStatus(Long ownerId, ProcessStatus processStatus);
}
