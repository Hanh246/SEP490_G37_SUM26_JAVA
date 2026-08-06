package com.sep.comiverse.repository;

import com.sep.comiverse.entity.AppealTicketEntity;
import com.sep.comiverse.entity.enums.AppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IAppealTicketRepository extends JpaRepository<AppealTicketEntity, UUID> {
    Page<AppealTicketEntity> findAllByAuthorId(UUID authorId, Pageable pageable);
    
    Page<AppealTicketEntity> findAllByStatus(AppealStatus status, Pageable pageable);

    Optional<AppealTicketEntity> findByTargetIdAndStatusIn(UUID targetId, List<AppealStatus> statuses);
}
