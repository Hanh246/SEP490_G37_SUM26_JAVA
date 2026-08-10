package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TranslatorEarningEntryEntity;
import com.sep.comiverse.entity.enums.TranslatorEarningEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ITranslatorEarningEntryRepository extends JpaRepository<TranslatorEarningEntryEntity, UUID> {

    List<TranslatorEarningEntryEntity> findAllBySettlementIdAndEntryType(
            UUID settlementId,
            TranslatorEarningEntryType entryType
    );

    List<TranslatorEarningEntryEntity> findAllByTranslatorIdAndEntryMonthOrderByCreatedAtAsc(
            UUID translatorId,
            String entryMonth
    );

    List<TranslatorEarningEntryEntity> findAllByTranslatorIdAndEntryMonthAndEntryTypeOrderByCreatedAtAsc(
            UUID translatorId,
            String entryMonth,
            TranslatorEarningEntryType entryType
    );

    @Query("""
            SELECT COALESCE(SUM(e.amountUsd), 0)
            FROM TranslatorEarningEntryEntity e
            WHERE e.translatorId = :translatorId
              AND e.entryMonth <= :throughMonth
              AND COALESCE(e.deleted, false) = false
            """)
    BigDecimal sumAmountThroughMonth(
            @Param("translatorId") UUID translatorId,
            @Param("throughMonth") String throughMonth
    );
}
