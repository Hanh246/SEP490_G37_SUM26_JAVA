package com.sep.comiverse.repository;

import com.sep.comiverse.entity.TranslatorEarningAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ITranslatorEarningAdjustmentRepository extends JpaRepository<TranslatorEarningAdjustmentEntity, UUID> {
    List<TranslatorEarningAdjustmentEntity> findAllByTranslatorIdAndAdjustmentMonthOrderByCreatedAtAsc(
            UUID translatorId,
            String adjustmentMonth
    );

    @Query("""
            SELECT COALESCE(SUM(a.amountUsd), 0)
            FROM TranslatorEarningAdjustmentEntity a
            WHERE a.translatorId = :translatorId
              AND a.adjustmentMonth <= :throughMonth
              AND COALESCE(a.deleted, false) = false
            """)
    BigDecimal sumAmountThroughMonth(
            @Param("translatorId") UUID translatorId,
            @Param("throughMonth") String throughMonth
    );
}
