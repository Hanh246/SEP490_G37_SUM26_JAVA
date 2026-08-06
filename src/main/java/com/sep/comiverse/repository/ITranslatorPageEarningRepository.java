package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TranslatorPageEarningEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ITranslatorPageEarningRepository extends JpaRepository<TranslatorPageEarningEntity, UUID> {
    List<TranslatorPageEarningEntity> findAllBySettlementId(UUID settlementId);

    List<TranslatorPageEarningEntity> findAllByTranslatorIdAndSettlementMonthOrderByCreatedAtAsc(
            UUID translatorId,
            String settlementMonth
    );

    @Query("""
            SELECT COALESCE(SUM(e.netAmountUsd), 0)
            FROM TranslatorPageEarningEntity e
            WHERE e.translatorId = :translatorId
              AND e.settlementMonth <= :throughMonth
              AND COALESCE(e.deleted, false) = false
            """)
    BigDecimal sumNetAmountThroughMonth(
            @Param("translatorId") UUID translatorId,
            @Param("throughMonth") String throughMonth
    );
}
