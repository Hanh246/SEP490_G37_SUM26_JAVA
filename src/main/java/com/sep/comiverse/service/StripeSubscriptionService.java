package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep.comiverse.dto.response.CheckoutSessionResponse;
import com.sep.comiverse.dto.response.CheckoutStatusResponse;
import com.sep.comiverse.dto.response.PaymentLogPageResponse;
import com.sep.comiverse.dto.response.PaymentLogResponse;
import com.sep.comiverse.dto.response.PortalSessionResponse;
import com.sep.comiverse.dto.response.ReaderPaymentHistoryPageResponse;
import com.sep.comiverse.dto.response.ReaderPaymentHistoryResponse;
import com.sep.comiverse.dto.response.ReaderSubscriptionResponse;
import com.sep.comiverse.entity.PaymentTransactionEntity;
import com.sep.comiverse.entity.ReaderSubscriptionEntity;
import com.sep.comiverse.entity.StripeWebhookEventEntity;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.PaymentTransactionStatus;
import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IPaymentTransactionRepository;
import com.sep.comiverse.repository.IReaderSubscriptionRepository;
import com.sep.comiverse.repository.IStripeWebhookEventRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeSubscriptionService {
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    private final SubscriptionPlanService planService;
    private final StripeGatewayService stripeGatewayService;
    private final IPaymentTransactionRepository paymentRepository;
    private final IReaderSubscriptionRepository subscriptionRepository;
    private final IStripeWebhookEventRepository webhookEventRepository;
    private final IUserRepository userRepository;

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(UUID userId, UUID planId) {
        UserEntity user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        String role = user.getRole() == null ? "" : user.getRole().getRoleName();
        if (!"READER".equalsIgnoreCase(role)) {
            throw new CustomException(403, "Only reader accounts can purchase a reader subscription", HttpStatus.FORBIDDEN);
        }

        Optional<ReaderSubscriptionEntity> existingSubscription =
                subscriptionRepository.findByUserIdAndDeletedFalse(userId);
        if (existingSubscription.isEmpty()
                && user.getPremiumExpiresAt() != null
                && user.getPremiumExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new CustomException(
                    409,
                    "Premium is already active for this account.",
                    HttpStatus.CONFLICT
            );
        }
        existingSubscription
                .filter(this::requiresBillingPortalBeforeNewCheckout)
                .ifPresent(active -> {
                    throw new CustomException(
                            409,
                            "An existing Stripe subscription must be managed before starting a new checkout.",
                            HttpStatus.CONFLICT
                    );
                });

        String existingCustomerId = existingSubscription
                .map(ReaderSubscriptionEntity::getStripeCustomerId)
                .filter(value -> !value.isBlank())
                .orElse(null);
        SubscriptionPlanEntity plan = planService.ensureStripeCatalog(planId);
        JsonNode session = stripeGatewayService.createCheckoutSession(
                userId,
                user.getEmail(),
                plan,
                existingCustomerId
        );
        String sessionId = requiredText(session, "id", "Stripe did not return a checkout session ID");
        String checkoutUrl = requiredText(session, "url", "Stripe did not return a checkout URL");

        Optional<PaymentTransactionEntity> existingTransaction =
                paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId);
        if (existingTransaction.isPresent()) {
            if (!existingTransaction.get().getUserId().equals(userId)) {
                throw new CustomException(409, "Stripe Checkout session belongs to another user", HttpStatus.CONFLICT);
            }
            return CheckoutSessionResponse.builder()
                    .sessionId(sessionId)
                    .checkoutUrl(checkoutUrl)
                    .build();
        }

        PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .userId(userId)
                .userEmail(user.getEmail())
                .planId(plan.getId())
                .planCode(plan.getCode())
                .planName(plan.getName())
                .amount(plan.getPrice())
                .currency(plan.getCurrency())
                .status(PaymentTransactionStatus.PENDING)
                .stripeCheckoutSessionId(sessionId)
                .build();
        paymentRepository.save(transaction);

        return CheckoutSessionResponse.builder()
                .sessionId(sessionId)
                .checkoutUrl(checkoutUrl)
                .build();
    }

    @Transactional
    public ReaderSubscriptionResponse getCurrentSubscription(UUID userId) {
        return subscriptionRepository.findByUserIdAndDeletedFalse(userId)
                .map(subscription -> {
                    syncPremiumCache(subscription);
                    return toSubscriptionResponse(subscription);
                })
                .orElse(null);
    }

    @Transactional
    public CheckoutStatusResponse getCheckoutStatus(UUID userId, String sessionId) {
        PaymentTransactionEntity transaction = paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new CustomException(404, "Payment session not found", HttpStatus.NOT_FOUND));
        if (!transaction.getUserId().equals(userId)) {
            throw new CustomException(403, "You cannot view this payment session", HttpStatus.FORBIDDEN);
        }

        // Webhook remains the primary source of truth. This reconciliation is a safe fallback
        // for local development or temporary webhook-delivery failures.
        if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
            try {
                JsonNode stripeSession = stripeGatewayService.retrieveCheckoutSession(sessionId);
                String checkoutStatus = stripeSession.path("status").asText("");
                String paymentStatus = stripeSession.path("payment_status").asText("");
                if ("complete".equalsIgnoreCase(checkoutStatus)
                        && ("paid".equalsIgnoreCase(paymentStatus)
                        || "no_payment_required".equalsIgnoreCase(paymentStatus))) {
                    handleCheckoutCompleted(stripeSession);
                    transaction = paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId)
                            .orElse(transaction);
                }
            } catch (RuntimeException ex) {
                // Do not fail the reader result page merely because Stripe reconciliation is unavailable.
                // Stripe will still retry the signed webhook.
                log.warn("Unable to reconcile pending Stripe Checkout session {}: {}", sessionId, ex.getMessage());
            }
        }

        ReaderSubscriptionEntity subscription = subscriptionRepository.findByUserIdAndDeletedFalse(userId).orElse(null);
        if (transaction.getStatus() == PaymentTransactionStatus.PAID
                && (subscription == null || !isSubscriptionCurrentlyActive(subscription))) {
            try {
                subscription = reconcilePaidSubscription(transaction);
            } catch (RuntimeException ex) {
                // Payment is already confirmed. Keep exposing PAID while Stripe/webhooks finish
                // subscription synchronization, so the frontend can continue polling safely.
                log.warn(
                        "Unable to reconcile paid Stripe Checkout session {} yet: {}",
                        sessionId,
                        ex.getMessage()
                );
            }
        }

        if (subscription != null) {
            syncPremiumCache(subscription);
        }
        boolean premiumActive = subscription != null && isSubscriptionCurrentlyActive(subscription);
        return CheckoutStatusResponse.builder()
                .sessionId(sessionId)
                .paymentStatus(transaction.getStatus())
                .planCode(transaction.getPlanCode())
                .planName(transaction.getPlanName())
                .premiumActive(premiumActive)
                .premiumExpiresAt(subscription == null ? null : subscription.getCurrentPeriodEnd())
                .build();
    }

    @Transactional(readOnly = true)
    public PortalSessionResponse createPortalSession(UUID userId) {
        ReaderSubscriptionEntity subscription = subscriptionRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(404, "No subscription found", HttpStatus.NOT_FOUND));
        if (subscription.getStripeCustomerId() == null || subscription.getStripeCustomerId().isBlank()) {
            throw new CustomException(409, "Stripe customer information is not available yet", HttpStatus.CONFLICT);
        }
        JsonNode portal = stripeGatewayService.createBillingPortalSession(subscription.getStripeCustomerId());
        return PortalSessionResponse.builder()
                .portalUrl(requiredText(portal, "url", "Stripe did not return a billing portal URL"))
                .build();
    }

    @Transactional
    public void processWebhook(JsonNode event) {
        String eventId = requiredText(event, "id", "Stripe event ID is missing");
        String eventType = requiredText(event, "type", "Stripe event type is missing");
        if (webhookEventRepository.existsByEventIdAndDeletedFalse(eventId)) {
            log.debug("Ignoring duplicate Stripe webhook event {}", eventId);
            return;
        }

        JsonNode object = event.path("data").path("object");
        log.info("Processing Stripe webhook event id={} type={}", eventId, eventType);
        switch (eventType) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> handleCheckoutCompleted(object);
            case "checkout.session.expired" -> handleCheckoutExpired(object);
            case "checkout.session.async_payment_failed" -> handleCheckoutAsyncPaymentFailed(object);
            case "invoice.paid" -> handleInvoicePaid(object);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(object);
            case "customer.subscription.updated", "customer.subscription.deleted" -> handleSubscriptionChanged(object);
            default -> log.debug("Stripe webhook event {} does not require local processing", eventType);
        }

        webhookEventRepository.save(StripeWebhookEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .processed(true)
                .build());
    }

    @Transactional(readOnly = true)
    public PaymentLogPageResponse getPaymentLogs(
            PaymentTransactionStatus status,
            String query,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentTransactionEntity> result = paymentRepository.searchAdminLogs(status, normalizeQuery(query), pageable);
        return PaymentLogPageResponse.builder()
                .content(result.getContent().stream().map(this::toPaymentLogResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public ReaderPaymentHistoryPageResponse getPaymentHistory(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<PaymentTransactionEntity> result =
                paymentRepository.findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
        return ReaderPaymentHistoryPageResponse.builder()
                .content(result.getContent().stream().map(this::toReaderPaymentHistoryResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private void handleCheckoutCompleted(JsonNode session) {
        String sessionId = requiredText(session, "id", "Stripe Checkout session ID is missing");
        PaymentTransactionEntity transaction = paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId)
                .orElseGet(() -> rebuildTransactionFromMetadata(session));

        String subscriptionId = textOrNull(session, "subscription");
        String customerId = textOrNull(session, "customer");
        String paymentIntentId = textOrNull(session, "payment_intent");
        String paymentStatus = session.path("payment_status").asText("");

        transaction.setStripeSubscriptionId(subscriptionId);
        transaction.setStripeCustomerId(customerId);
        transaction.setStripePaymentIntentId(paymentIntentId);

        if ("paid".equalsIgnoreCase(paymentStatus) || "no_payment_required".equalsIgnoreCase(paymentStatus)) {
            transaction.setStatus(PaymentTransactionStatus.PAID);
            if (transaction.getPaidAt() == null) {
                transaction.setPaidAt(Instant.now());
            }
            transaction.setFailureReason(null);

            // Persist the payment confirmation before synchronizing subscription details.
            // A temporary failure while retrieving the Subscription must not roll the payment log back to PENDING.
            PaymentTransactionEntity savedTransaction = paymentRepository.saveAndFlush(transaction);

            if (subscriptionId != null) {
                try {
                    JsonNode stripeSubscription = stripeGatewayService.retrieveSubscription(subscriptionId);
                    ReaderSubscriptionEntity subscription = upsertSubscription(
                            stripeSubscription,
                            savedTransaction,
                            customerId
                    );
                    syncPremiumCache(subscription);
                } catch (RuntimeException ex) {
                    // invoice.paid/customer.subscription.updated will retry subscription synchronization.
                    log.error(
                            "Stripe payment {} was confirmed, but subscription {} could not be synchronized yet: {}",
                            sessionId,
                            subscriptionId,
                            ex.getMessage(),
                            ex
                    );
                }
            }
            return;
        }

        // A completed Checkout can still be unpaid for asynchronous payment methods.
        // Keep PENDING until async_payment_succeeded or invoice.paid is received.
        paymentRepository.save(transaction);
    }

    private void handleCheckoutAsyncPaymentFailed(JsonNode session) {
        String sessionId = textOrNull(session, "id");
        if (sessionId == null) return;
        paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId).ifPresent(transaction -> {
            String subscriptionId = textOrNull(session, "subscription");
            String customerId = textOrNull(session, "customer");
            transaction.setStatus(PaymentTransactionStatus.FAILED);
            transaction.setFailureReason("Stripe asynchronous payment failed");
            transaction.setStripeSubscriptionId(subscriptionId);
            transaction.setStripeCustomerId(customerId);
            PaymentTransactionEntity saved = paymentRepository.save(transaction);

            if (subscriptionId != null) {
                try {
                    JsonNode stripeSubscription = stripeGatewayService.retrieveSubscription(subscriptionId);
                    ReaderSubscriptionEntity subscription = upsertSubscription(
                            stripeSubscription,
                            saved,
                            customerId
                    );
                    syncPremiumCache(subscription);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Unable to synchronize failed asynchronous subscription {}: {}",
                            subscriptionId,
                            ex.getMessage()
                    );
                }
            }
        });
    }

    private void handleCheckoutExpired(JsonNode session) {
        String sessionId = session.path("id").asText();
        paymentRepository.findByStripeCheckoutSessionIdAndDeletedFalse(sessionId).ifPresent(transaction -> {
            if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
                transaction.setStatus(PaymentTransactionStatus.EXPIRED);
                transaction.setFailureReason("Stripe Checkout session expired before payment was completed");
                paymentRepository.save(transaction);
            }
        });
    }

    private void handleInvoicePaid(JsonNode invoice) {
        String subscriptionId = extractSubscriptionIdFromInvoice(invoice);
        if (subscriptionId == null) return;
        JsonNode stripeSubscription = stripeGatewayService.retrieveSubscription(subscriptionId);
        UUID userId = uuidFromMetadata(stripeSubscription, "user_id");
        UUID planId = uuidFromMetadata(stripeSubscription, "plan_id");
        if (userId == null || planId == null) return;

        SubscriptionPlanEntity plan = planService.getPlanEntity(planId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(404, "Subscription user not found", HttpStatus.NOT_FOUND));

        String invoiceId = textOrNull(invoice, "id");
        PaymentTransactionEntity transaction = Optional.ofNullable(invoiceId)
                .flatMap(paymentRepository::findByStripeInvoiceIdAndDeletedFalse)
                .or(() -> paymentRepository
                        .findFirstByStripeSubscriptionIdAndDeletedFalseOrderByCreatedAtDesc(subscriptionId)
                        .filter(existing -> existing.getStripeInvoiceId() == null))
                .or(() -> paymentRepository
                        .findFirstByUserIdAndPlanIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
                                userId,
                                planId,
                                PaymentTransactionStatus.PENDING
                        ))
                .orElseGet(() -> PaymentTransactionEntity.builder()
                        .userId(userId)
                        .userEmail(user.getEmail())
                        .planId(planId)
                        .planCode(plan.getCode())
                        .planName(plan.getName())
                        .amount(readInvoiceAmount(invoice, plan))
                        .currency(invoice.path("currency").asText(plan.getCurrency()).toUpperCase(Locale.ROOT))
                        .status(PaymentTransactionStatus.PAID)
                        .build());

        transaction.setStripeSubscriptionId(subscriptionId);
        transaction.setStatus(PaymentTransactionStatus.PAID);
        transaction.setPaidAt(Instant.now());
        transaction.setStripeInvoiceId(invoiceId);
        transaction.setStripeCustomerId(textOrNull(invoice, "customer"));
        transaction.setFailureReason(null);
        PaymentTransactionEntity saved = paymentRepository.save(transaction);

        ReaderSubscriptionEntity subscription = upsertSubscription(
                stripeSubscription,
                saved,
                textOrNull(invoice, "customer")
        );
        syncPremiumCache(subscription);
    }

    private void handleInvoicePaymentFailed(JsonNode invoice) {
        String subscriptionId = extractSubscriptionIdFromInvoice(invoice);
        if (subscriptionId == null) return;
        String reason = invoice.path("last_finalization_error").path("message").asText("Stripe invoice payment failed");
        String invoiceId = textOrNull(invoice, "id");
        JsonNode stripeSubscription = stripeGatewayService.retrieveSubscription(subscriptionId);
        UUID userId = uuidFromMetadata(stripeSubscription, "user_id");
        UUID planId = uuidFromMetadata(stripeSubscription, "plan_id");
        PaymentTransactionEntity transaction = Optional.ofNullable(invoiceId)
                .flatMap(paymentRepository::findByStripeInvoiceIdAndDeletedFalse)
                .orElse(null);

        if (transaction == null && userId != null && planId != null) {
            SubscriptionPlanEntity plan = planService.getPlanEntity(planId);
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(404, "Subscription user not found", HttpStatus.NOT_FOUND));
            transaction = PaymentTransactionEntity.builder()
                    .userId(userId)
                    .userEmail(user.getEmail())
                    .planId(planId)
                    .planCode(plan.getCode())
                    .planName(plan.getName())
                    .amount(readInvoiceAmount(invoice, plan))
                    .currency(invoice.path("currency").asText(plan.getCurrency()).toUpperCase(Locale.ROOT))
                    .status(PaymentTransactionStatus.FAILED)
                    .stripeSubscriptionId(subscriptionId)
                    .build();
        }

        PaymentTransactionEntity savedTransaction = null;
        if (transaction != null) {
            transaction.setStatus(PaymentTransactionStatus.FAILED);
            transaction.setFailureReason(reason);
            transaction.setStripeInvoiceId(invoiceId);
            transaction.setStripeCustomerId(textOrNull(invoice, "customer"));
            savedTransaction = paymentRepository.save(transaction);
        }

        ReaderSubscriptionEntity subscription = subscriptionRepository
                .findByStripeSubscriptionIdAndDeletedFalse(subscriptionId)
                .orElse(null);
        if (subscription == null && savedTransaction != null) {
            subscription = upsertSubscription(
                    stripeSubscription,
                    savedTransaction,
                    textOrNull(invoice, "customer")
            );
        }
        if (subscription != null) {
            subscription.setStatus(ReaderSubscriptionStatus.PAST_DUE);
            subscription = subscriptionRepository.save(subscription);
            syncPremiumCache(subscription);
        }
    }

    private void handleSubscriptionChanged(JsonNode stripeSubscription) {
        String subscriptionId = textOrNull(stripeSubscription, "id");
        if (subscriptionId == null) return;
        PaymentTransactionEntity transaction = paymentRepository
                .findFirstByStripeSubscriptionIdAndDeletedFalseOrderByCreatedAtDesc(subscriptionId)
                .orElse(null);
        UUID userId = uuidFromMetadata(stripeSubscription, "user_id");
        UUID planId = uuidFromMetadata(stripeSubscription, "plan_id");
        if (transaction == null && (userId == null || planId == null)) return;

        ReaderSubscriptionEntity subscription = upsertSubscription(
                stripeSubscription,
                transaction,
                textOrNull(stripeSubscription, "customer")
        );
        syncPremiumCache(subscription);
    }

    private ReaderSubscriptionEntity upsertSubscription(
            JsonNode stripeSubscription,
            PaymentTransactionEntity transaction,
            String fallbackCustomerId
    ) {
        String subscriptionId = requiredText(stripeSubscription, "id", "Stripe subscription ID is missing");
        UUID userId = transaction != null ? transaction.getUserId() : uuidFromMetadata(stripeSubscription, "user_id");
        UUID planId = transaction != null ? transaction.getPlanId() : uuidFromMetadata(stripeSubscription, "plan_id");
        if (userId == null || planId == null) {
            throw new CustomException(400, "Stripe subscription metadata is incomplete", HttpStatus.BAD_REQUEST);
        }
        SubscriptionPlanEntity plan = planService.getPlanEntity(planId);

        ReaderSubscriptionEntity subscription = subscriptionRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(() -> ReaderSubscriptionEntity.builder().userId(userId).build());
        subscription.setPlanId(planId);
        subscription.setPlanCode(plan.getCode());
        subscription.setPlanName(plan.getName());
        subscription.setStatus(parseSubscriptionStatus(stripeSubscription.path("status").asText()));
        subscription.setStripeSubscriptionId(subscriptionId);
        subscription.setStripeCustomerId(Optional.ofNullable(textOrNull(stripeSubscription, "customer")).orElse(fallbackCustomerId));
        subscription.setCurrentPeriodStart(epochToInstant(readPeriodEpoch(stripeSubscription, "current_period_start")));
        subscription.setCurrentPeriodEnd(epochToInstant(readPeriodEpoch(stripeSubscription, "current_period_end")));
        subscription.setCancelAtPeriodEnd(stripeSubscription.path("cancel_at_period_end").asBoolean(false));
        if (transaction != null) subscription.setLatestPaymentTransactionId(transaction.getId());
        return subscriptionRepository.save(subscription);
    }

    private PaymentTransactionEntity rebuildTransactionFromMetadata(JsonNode session) {
        UUID userId = uuidFromMetadata(session, "user_id");
        UUID planId = uuidFromMetadata(session, "plan_id");
        if (userId == null || planId == null) {
            throw new CustomException(400, "Stripe Checkout metadata is incomplete", HttpStatus.BAD_REQUEST);
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(404, "Payment user not found", HttpStatus.NOT_FOUND));
        SubscriptionPlanEntity plan = planService.getPlanEntity(planId);
        return PaymentTransactionEntity.builder()
                .userId(userId)
                .userEmail(user.getEmail())
                .planId(planId)
                .planCode(plan.getCode())
                .planName(plan.getName())
                .amount(plan.getPrice())
                .currency(plan.getCurrency())
                .status(PaymentTransactionStatus.PENDING)
                .stripeCheckoutSessionId(session.path("id").asText())
                .build();
    }

    private ReaderSubscriptionEntity reconcilePaidSubscription(PaymentTransactionEntity transaction) {
        String subscriptionId = transaction.getStripeSubscriptionId();
        String customerId = transaction.getStripeCustomerId();

        if ((subscriptionId == null || subscriptionId.isBlank())
                && transaction.getStripeCheckoutSessionId() != null
                && !transaction.getStripeCheckoutSessionId().isBlank()) {
            JsonNode session = stripeGatewayService.retrieveCheckoutSession(transaction.getStripeCheckoutSessionId());
            subscriptionId = textOrNull(session, "subscription");
            customerId = Optional.ofNullable(textOrNull(session, "customer")).orElse(customerId);
            transaction.setStripeSubscriptionId(subscriptionId);
            transaction.setStripeCustomerId(customerId);
            paymentRepository.save(transaction);
        }

        if (subscriptionId == null || subscriptionId.isBlank()) {
            return null;
        }

        JsonNode stripeSubscription = stripeGatewayService.retrieveSubscription(subscriptionId);
        ReaderSubscriptionEntity subscription = upsertSubscription(
                stripeSubscription,
                transaction,
                customerId
        );
        syncPremiumCache(subscription);
        return subscription;
    }

    private void syncPremiumCache(ReaderSubscriptionEntity subscription) {
        if (isSubscriptionCurrentlyActive(subscription)) {
            grantPremium(subscription);
        } else {
            revokePremium(subscription.getUserId());
        }
    }

    private void grantPremium(ReaderSubscriptionEntity subscription) {
        if (subscription.getCurrentPeriodEnd() == null) return;
        UserEntity user = userRepository.findById(subscription.getUserId())
                .orElseThrow(() -> new CustomException(404, "Subscription user not found", HttpStatus.NOT_FOUND));
        LocalDateTime expiresAt = LocalDateTime.ofInstant(subscription.getCurrentPeriodEnd(), ZoneOffset.UTC);
        if (!Objects.equals(user.getPremiumPlan(), subscription.getPlanCode())
                || !Objects.equals(user.getPremiumExpiresAt(), expiresAt)) {
            user.setPremiumPlan(subscription.getPlanCode());
            user.setPremiumExpiresAt(expiresAt);
            userRepository.save(user);
        }
    }

    private void revokePremium(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getPremiumPlan() != null || user.getPremiumExpiresAt() != null) {
                user.setPremiumPlan(null);
                user.setPremiumExpiresAt(null);
                userRepository.save(user);
            }
        });
    }

    private ReaderSubscriptionResponse toSubscriptionResponse(ReaderSubscriptionEntity subscription) {
        return ReaderSubscriptionResponse.builder()
                .id(subscription.getId())
                .planId(subscription.getPlanId())
                .planCode(subscription.getPlanCode())
                .planName(subscription.getPlanName())
                .status(subscription.getStatus())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
                .premiumActive(isSubscriptionCurrentlyActive(subscription))
                .requiresBillingManagement(requiresBillingPortalBeforeNewCheckout(subscription))
                .billingPortalAvailable(subscription.getStripeCustomerId() != null
                        && !subscription.getStripeCustomerId().isBlank())
                .build();
    }

    private PaymentLogResponse toPaymentLogResponse(PaymentTransactionEntity transaction) {
        return PaymentLogResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .userEmail(transaction.getUserEmail())
                .planId(transaction.getPlanId())
                .planCode(transaction.getPlanCode())
                .planName(transaction.getPlanName())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .provider(transaction.getProvider())
                .stripeCheckoutSessionId(transaction.getStripeCheckoutSessionId())
                .stripeSubscriptionId(transaction.getStripeSubscriptionId())
                .stripeInvoiceId(transaction.getStripeInvoiceId())
                .failureReason(transaction.getFailureReason())
                .paidAt(transaction.getPaidAt())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private ReaderPaymentHistoryResponse toReaderPaymentHistoryResponse(PaymentTransactionEntity transaction) {
        return ReaderPaymentHistoryResponse.builder()
                .id(transaction.getId())
                .planCode(transaction.getPlanCode())
                .planName(transaction.getPlanName())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .provider(transaction.getProvider())
                .failureReason(transaction.getFailureReason())
                .paidAt(transaction.getPaidAt())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private boolean requiresBillingPortalBeforeNewCheckout(ReaderSubscriptionEntity subscription) {
        if (subscription == null
                || subscription.getStripeSubscriptionId() == null
                || subscription.getStripeSubscriptionId().isBlank()) {
            return false;
        }
        return subscription.getStatus() == ReaderSubscriptionStatus.ACTIVE
                || subscription.getStatus() == ReaderSubscriptionStatus.TRIALING
                || subscription.getStatus() == ReaderSubscriptionStatus.PAST_DUE
                || subscription.getStatus() == ReaderSubscriptionStatus.UNPAID
                || subscription.getStatus() == ReaderSubscriptionStatus.PAUSED
                || subscription.getStatus() == ReaderSubscriptionStatus.INCOMPLETE;
    }

    private boolean isSubscriptionCurrentlyActive(ReaderSubscriptionEntity subscription) {
        boolean activeStatus = subscription.getStatus() == ReaderSubscriptionStatus.ACTIVE
                || subscription.getStatus() == ReaderSubscriptionStatus.TRIALING;
        return activeStatus
                && subscription.getCurrentPeriodEnd() != null
                && subscription.getCurrentPeriodEnd().isAfter(Instant.now());
    }

    private ReaderSubscriptionStatus parseSubscriptionStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) return ReaderSubscriptionStatus.UNKNOWN;
        try {
            return ReaderSubscriptionStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ReaderSubscriptionStatus.UNKNOWN;
        }
    }

    private UUID uuidFromMetadata(JsonNode object, String key) {
        String raw = object.path("metadata").path(key).asText();
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long readPeriodEpoch(JsonNode subscription, String field) {
        if (subscription.hasNonNull(field)) return subscription.path(field).asLong();
        JsonNode firstItem = subscription.path("items").path("data");
        if (firstItem.isArray() && !firstItem.isEmpty() && firstItem.get(0).hasNonNull(field)) {
            return firstItem.get(0).path(field).asLong();
        }
        return null;
    }

    private String extractSubscriptionIdFromInvoice(JsonNode invoice) {
        String direct = textOrNull(invoice, "subscription");
        if (direct != null) return direct;
        String parentSubscription = invoice.path("parent").path("subscription_details").path("subscription").asText();
        if (!parentSubscription.isBlank()) return parentSubscription;
        JsonNode lines = invoice.path("lines").path("data");
        if (lines.isArray()) {
            for (JsonNode line : lines) {
                String nested = line.path("parent").path("subscription_item_details").path("subscription").asText();
                if (!nested.isBlank()) return nested;
            }
        }
        return null;
    }

    private BigDecimal readInvoiceAmount(JsonNode invoice, SubscriptionPlanEntity plan) {
        long minorValue = invoice.path("amount_paid").asLong(0L);
        if (minorValue <= 0L) {
            minorValue = invoice.path("amount_due").asLong(0L);
        }
        if (minorValue <= 0L) return plan.getPrice();

        BigDecimal minor = BigDecimal.valueOf(minorValue);
        String currency = invoice.path("currency").asText(plan.getCurrency()).toUpperCase(Locale.ROOT);
        return ZERO_DECIMAL_CURRENCIES.contains(currency)
                ? minor
                : minor.divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
    }

    private Instant epochToInstant(Long epochSeconds) {
        return epochSeconds == null || epochSeconds <= 0 ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = node == null ? "" : node.path(field).asText();
        if (value.isBlank()) {
            throw new CustomException(400, message, HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String normalizeQuery(String query) {
        if (query == null) return null;
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
