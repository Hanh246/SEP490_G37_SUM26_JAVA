package com.sep.comiverse.repository;

import com.sep.comiverse.entity.PaymentTransactionEntity;
import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface IPaymentTransactionRepository extends AbstractCrudRepository<PaymentTransactionEntity, UUID> {

    Optional<PaymentTransactionEntity> findByStripeCheckoutSessionIdAndDeletedFalse(String sessionId);

    Optional<PaymentTransactionEntity> findByStripeInvoiceIdAndDeletedFalse(String invoiceId);

    Optional<PaymentTransactionEntity> findFirstByStripeSubscriptionIdAndDeletedFalseOrderByCreatedAtDesc(String subscriptionId);

    Optional<PaymentTransactionEntity> findFirstByUserIdAndPlanIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId,
            UUID planId,
            PaymentTransactionStatus status
    );

    Page<PaymentTransactionEntity> findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM PaymentTransactionEntity p
            WHERE p.deleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (
                    :query IS NULL OR :query = ''
                    OR LOWER(p.userEmail) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.planName) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(p.planCode) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(p.stripeCheckoutSessionId, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(p.stripeSubscriptionId, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            """)
    Page<PaymentTransactionEntity> searchAdminLogs(
            @Param("status") PaymentTransactionStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM PaymentTransactionEntity p
            WHERE p.deleted = false
              AND UPPER(p.currency) = UPPER(:currency)
              AND (
                    (p.createdAt >= :from AND p.createdAt < :to)
                    OR (p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :to)
              )
            """)
    List<PaymentTransactionEntity> findForStatistics(
            @Param("currency") String currency,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT DISTINCT UPPER(p.currency)
            FROM PaymentTransactionEntity p
            WHERE p.deleted = false
            ORDER BY UPPER(p.currency)
            """)
    List<String> findDistinctCurrencies();
}
