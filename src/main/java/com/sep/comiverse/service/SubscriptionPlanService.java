package com.sep.comiverse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.SubscriptionPlanRequest;
import com.sep.comiverse.dto.response.SubscriptionPlanResponse;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.enums.BillingInterval;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ISubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {
    private static final List<String> DEFAULT_FEATURES = List.of(
            "Read without ads",
            "Premium-only titles",
            "Offline reading",
            "HD quality",
            "Unlimited chapters per day"
    );

    private final ISubscriptionPlanRepository planRepository;
    private final StripeGatewayService stripeGatewayService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getActivePlans() {
        return planRepository.findAllByActiveTrueAndDeletedFalseOrderBySortOrderAscCreatedAtAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getAllPlans() {
        return planRepository.findAllByDeletedFalseOrderBySortOrderAscCreatedAtAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanEntity getActivePlanEntity(UUID planId) {
        SubscriptionPlanEntity plan = getPlanEntity(planId);
        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new CustomException(400, "This subscription plan is inactive", HttpStatus.BAD_REQUEST);
        }
        return plan;
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanEntity getPlanEntity(UUID planId) {
        return planRepository.findByIdAndDeletedFalse(planId)
                .orElseThrow(() -> new CustomException(404, "Subscription plan not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request) {
        String code = normalizeCode(request.getCode());
        if (planRepository.existsByCodeIgnoreCaseAndDeletedFalse(code)) {
            throw new CustomException(409, "Subscription plan code already exists", HttpStatus.CONFLICT);
        }

        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
                .code(code)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .price(request.getPrice().stripTrailingZeros())
                .currency(normalizeCurrency(request.getCurrency()))
                .billingInterval(request.getBillingInterval())
                .intervalCount(defaultIfNull(request.getIntervalCount(), 1))
                .active(defaultIfNull(request.getActive(), true))
                .recommended(defaultIfNull(request.getRecommended(), false))
                .badge(trimToNull(request.getBadge()))
                .featuresJson(writeFeatures(request.getFeatures()))
                .sortOrder(defaultIfNull(request.getSortOrder(), 0))
                .build();
        return toResponse(planRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanResponse updatePlan(UUID planId, SubscriptionPlanRequest request) {
        SubscriptionPlanEntity plan = getPlanEntity(planId);
        String code = normalizeCode(request.getCode());
        planRepository.findByCodeIgnoreCaseAndDeletedFalse(code)
                .filter(found -> !found.getId().equals(planId))
                .ifPresent(found -> {
                    throw new CustomException(409, "Subscription plan code already exists", HttpStatus.CONFLICT);
                });

        String name = request.getName().trim();
        String description = trimToNull(request.getDescription());
        BigDecimal price = request.getPrice().stripTrailingZeros();
        String currency = normalizeCurrency(request.getCurrency());
        BillingInterval interval = request.getBillingInterval();
        Integer intervalCount = defaultIfNull(request.getIntervalCount(), 1);

        boolean stripeProductChanged = !Objects.equals(plan.getCode(), code)
                || !Objects.equals(plan.getName(), name)
                || !Objects.equals(plan.getDescription(), description);
        boolean stripePriceChanged = plan.getPrice().compareTo(price) != 0
                || !Objects.equals(plan.getCurrency(), currency)
                || plan.getBillingInterval() != interval
                || !Objects.equals(plan.getIntervalCount(), intervalCount);

        plan.setCode(code);
        plan.setName(name);
        plan.setDescription(description);
        plan.setPrice(price);
        plan.setCurrency(currency);
        plan.setBillingInterval(interval);
        plan.setIntervalCount(intervalCount);
        plan.setActive(defaultIfNull(request.getActive(), plan.getActive()));
        plan.setRecommended(defaultIfNull(request.getRecommended(), plan.getRecommended()));
        plan.setBadge(trimToNull(request.getBadge()));
        plan.setFeaturesJson(writeFeatures(request.getFeatures()));
        plan.setSortOrder(defaultIfNull(request.getSortOrder(), plan.getSortOrder()));

        if (stripeProductChanged && plan.getStripeProductId() != null && !plan.getStripeProductId().isBlank()) {
            stripeGatewayService.updateProduct(plan);
        }
        if (stripePriceChanged) {
            plan.setStripePriceId(null);
        }
        return toResponse(planRepository.save(plan));
    }

    @Transactional
    public SubscriptionPlanResponse updateStatus(UUID planId, boolean active) {
        SubscriptionPlanEntity plan = getPlanEntity(planId);
        plan.setActive(active);
        return toResponse(planRepository.save(plan));
    }

    @Transactional
    public synchronized SubscriptionPlanEntity ensureStripeCatalog(UUID planId) {
        SubscriptionPlanEntity plan = getActivePlanEntity(planId);
        boolean changed = false;
        if (plan.getStripeProductId() == null || plan.getStripeProductId().isBlank()) {
            plan.setStripeProductId(stripeGatewayService.createProduct(plan));
            changed = true;
        }
        if (plan.getStripePriceId() == null || plan.getStripePriceId().isBlank()) {
            plan.setStripePriceId(stripeGatewayService.createRecurringPrice(plan, plan.getStripeProductId()));
            changed = true;
        }
        return changed ? planRepository.save(plan) : plan;
    }

    public SubscriptionPlanResponse toResponse(SubscriptionPlanEntity plan) {
        return SubscriptionPlanResponse.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .price(plan.getPrice())
                .currency(plan.getCurrency())
                .billingInterval(plan.getBillingInterval())
                .intervalCount(plan.getIntervalCount())
                .active(plan.getActive())
                .recommended(plan.getRecommended())
                .badge(plan.getBadge())
                .features(readFeatures(plan.getFeaturesJson()))
                .sortOrder(plan.getSortOrder())
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaultPlans() {
        boolean created = false;
        if (planRepository.findByCodeIgnoreCaseAndDeletedFalse("MONTHLY").isEmpty()) {
            createDefaultPlan("MONTHLY", "Premium Monthly", new BigDecimal("79000"), BillingInterval.MONTH, 1, true, "Most Popular", 10);
            created = true;
        }
        if (planRepository.findByCodeIgnoreCaseAndDeletedFalse("YEARLY").isEmpty()) {
            createDefaultPlan("YEARLY", "Premium Yearly", new BigDecimal("790000"), BillingInterval.YEAR, 1, false, "Save more", 20);
            created = true;
        }
        if (created) {
            log.info("Ensured default monthly and yearly subscription plans exist");
        }
    }

    private void createDefaultPlan(
            String code,
            String name,
            BigDecimal price,
            BillingInterval interval,
            int intervalCount,
            boolean recommended,
            String badge,
            int sortOrder
    ) {
        planRepository.save(SubscriptionPlanEntity.builder()
                .code(code)
                .name(name)
                .description("ComiVerse premium reader subscription")
                .price(price)
                .currency("VND")
                .billingInterval(interval)
                .intervalCount(intervalCount)
                .active(true)
                .recommended(recommended)
                .badge(badge)
                .featuresJson(writeFeatures(DEFAULT_FEATURES))
                .sortOrder(sortOrder)
                .build());
    }

    private String writeFeatures(List<String> rawFeatures) {
        List<String> features = sanitizeFeatures(rawFeatures);
        try {
            return objectMapper.writeValueAsString(features);
        } catch (Exception ex) {
            throw new CustomException(500, "Unable to save subscription plan features", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> readFeatures(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> features = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return features == null ? List.of() : features;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> sanitizeFeatures(List<String> rawFeatures) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (rawFeatures != null) {
            for (String feature : rawFeatures) {
                if (feature == null) continue;
                String trimmed = feature.trim();
                if (!trimmed.isEmpty()) unique.add(trimmed);
            }
        }
        if (unique.size() > 15) {
            throw new CustomException(400, "A plan can contain at most 15 features", HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(unique);
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> T defaultIfNull(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
