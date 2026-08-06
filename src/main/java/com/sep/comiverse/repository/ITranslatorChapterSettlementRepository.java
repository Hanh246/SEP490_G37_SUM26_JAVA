package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TranslatorChapterSettlementEntity;
import com.sep.comiverse.entity.enums.TranslatorSettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ITranslatorChapterSettlementRepository extends JpaRepository<TranslatorChapterSettlementEntity, UUID> {
    Optional<TranslatorChapterSettlementEntity> findFirstByTaskIdOrderByVersionNoDesc(UUID taskId);
    Optional<TranslatorChapterSettlementEntity> findFirstByTaskIdAndStatusOrderByVersionNoDesc(
            UUID taskId,
            TranslatorSettlementStatus status
    );
}
