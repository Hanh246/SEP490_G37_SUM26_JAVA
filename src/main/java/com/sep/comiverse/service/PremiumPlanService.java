package com.sep.comiverse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.UpdatePremiumPlanSettingsRequest;
import com.sep.comiverse.dto.response.PremiumPlanSettingsResponse;
import com.sep.comiverse.dto.response.UpgradePlanResponse;
import com.sep.comiverse.dto.response.UserProfileResponse;
import com.sep.comiverse.entity.ReaderSubscriptionEntity;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.BillingInterval;
import com.sep.comiverse.entity.enums.ReaderSubscriptionStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IReaderSubscriptionRepository;
import com.sep.comiverse.repository.ISubscriptionPlanRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PremiumPlanService {
    private static final BigDecimal DEFAULT_MONTHLY_PRICE = new BigDecimal("3.16");
    private static final BigDecimal DEFAULT_YEARLY_PRICE = new BigDecimal("31.60");
    private static final List<String> DEFAULT_BENEFITS = List.of(
            "Read without ads",
            "Early access to newest chapters",
            "Offline chapter downloads",
            "Exclusive Premium badge",
            "Priority support"
    );

    private final ISubscriptionPlanRepository subscriptionPlanRepository;
    private final IReaderSubscriptionRepository readerSubscriptionRepository;
    private final IUserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PremiumPlanSettingsResponse getPremiumPlanSettings() {
        SubscriptionPlanEntity monthly = findPlan("MONTHLY");
        SubscriptionPlanEntity yearly = findPlan("YEARLY");
        SubscriptionPlanEntity benefitsSource = monthly != null ? monthly : yearly;
        return PremiumPlanSettingsResponse.builder()
                .monthlyPrice(monthly == null ? DEFAULT_MONTHLY_PRICE : monthly.getPrice())
                .yearlyPrice(yearly == null ? DEFAULT_YEARLY_PRICE : yearly.getPrice())
                .benefits(readBenefits(benefitsSource))
                .build();
    }

    @Transactional
    public PremiumPlanSettingsResponse updatePremiumPlanSettings(UpdatePremiumPlanSettingsRequest request) {
        List<String> sanitizedBenefits = sanitizeBenefits(request.getBenefits());
        updateCanonicalPlan("MONTHLY", request.getMonthlyPrice(), sanitizedBenefits);
        updateCanonicalPlan("YEARLY", request.getYearlyPrice(), sanitizedBenefits);
        return getPremiumPlanSettings();
    }

    /**
     * Legacy/manual upgrade support retained for compatibility. Public readers are routed through
     * verified Stripe Checkout by PremiumPlanController, so this method is not an unverified payment path.
     */
    @Transactional
    public UpgradePlanResponse upgradePlan(UUID userId, String rawPlanType) {
        String planType = normalizePlanType(rawPlanType);
        UserEntity user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime base = user.getPremiumExpiresAt() != null && user.getPremiumExpiresAt().isAfter(now)
                ? user.getPremiumExpiresAt()
                : now;
        LocalDateTime expiresAt = "YEARLY".equals(planType) ? base.plusYears(1) : base.plusMonths(1);

        user.setPremiumPlan(planType);
        user.setPremiumExpiresAt(expiresAt);
        UserEntity saved = userRepository.save(user);

        UserProfileResponse profile = toUserProfileResponse(saved);
        return UpgradePlanResponse.builder()
                .planType(planType)
                .premiumExpiresAt(expiresAt)
                .premiumActive(true)
                .user(profile)
                .build();
    }

    /**
     * ReaderSubscription is authoritative whenever it exists. The premium fields on users are only
     * a denormalized cache kept for profile payload compatibility and legacy records.
     */
    @Transactional(readOnly = true)
    public boolean hasActivePremium(UserEntity user) {
        return resolvePremiumSnapshot(user).active();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse toUserProfileResponse(UserEntity user) {
        PremiumSnapshot premium = resolvePremiumSnapshot(user);
        java.util.List<String> parsedLangs = new java.util.ArrayList<>();
        if (user.getAssignedLanguages() != null && !user.getAssignedLanguages().isBlank()) {
            for (String lang : user.getAssignedLanguages().split(",")) {
                parsedLangs.add(lang.trim());
            }
        }
        return UserProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().getRoleName() : "READER")
                .avatarUrl(user.getAvatarUrl())
                .backgroundImageUrl(user.getBackgroundImageUrl())
                .dateOfBirth(user.getDateOfBirth())
                .bio(user.getBio())
                .premiumPlan(premium.active() ? premium.planCode() : null)
                .premiumExpiresAt(premium.expiresAt())
                .premiumActive(premium.active())
                .assignedLanguages(parsedLangs)
                .build();
    }

    private PremiumSnapshot resolvePremiumSnapshot(UserEntity user) {
        if (user == null) {
            return PremiumSnapshot.inactive(null);
        }

        if (user.getId() != null) {
            ReaderSubscriptionEntity subscription = readerSubscriptionRepository
                    .findByUserIdAndDeletedFalse(user.getId())
                    .orElse(null);
            if (subscription != null) {
                LocalDateTime expiresAt = subscription.getCurrentPeriodEnd() == null
                        ? null
                        : LocalDateTime.ofInstant(subscription.getCurrentPeriodEnd(), ZoneOffset.UTC);
                boolean active = isSubscriptionActive(subscription);
                return new PremiumSnapshot(active, active ? subscription.getPlanCode() : null, expiresAt);
            }
        }

        LocalDateTime expiresAt = user.getPremiumExpiresAt();
        boolean legacyActive = expiresAt != null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC));
        return new PremiumSnapshot(legacyActive, legacyActive ? user.getPremiumPlan() : null, expiresAt);
    }

    private boolean isSubscriptionActive(ReaderSubscriptionEntity subscription) {
        boolean activeStatus = subscription.getStatus() == ReaderSubscriptionStatus.ACTIVE
                || subscription.getStatus() == ReaderSubscriptionStatus.TRIALING;
        return activeStatus
                && subscription.getCurrentPeriodEnd() != null
                && subscription.getCurrentPeriodEnd().isAfter(Instant.now());
    }

    private String normalizePlanType(String rawPlanType) {
        String planType = rawPlanType == null ? "" : rawPlanType.trim().toUpperCase();
        if (!"MONTHLY".equals(planType) && !"YEARLY".equals(planType)) {
            throw new CustomException(400, "Invalid plan type", HttpStatus.BAD_REQUEST);
        }
        return planType;
    }

    private List<String> sanitizeBenefits(List<String> benefits) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (benefits != null) {
            for (String benefit : benefits) {
                if (benefit == null) continue;
                String trimmed = benefit.trim();
                if (!trimmed.isEmpty()) {
                    unique.add(trimmed);
                }
            }
        }
        if (unique.isEmpty()) {
            throw new CustomException(400, "At least one premium benefit is required", HttpStatus.BAD_REQUEST);
        }
        if (unique.size() > 15) {
            throw new CustomException(400, "Premium benefits can contain at most 15 items", HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(unique);
    }

    private SubscriptionPlanEntity findPlan(String code) {
        return subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse(code).orElse(null);
    }

    private List<String> readBenefits(SubscriptionPlanEntity plan) {
        if (plan == null || plan.getFeaturesJson() == null || plan.getFeaturesJson().isBlank()) {
            return DEFAULT_BENEFITS;
        }
        try {
            List<String> benefits = objectMapper.readValue(
                    plan.getFeaturesJson(),
                    new TypeReference<List<String>>() {}
            );
            return benefits == null || benefits.isEmpty() ? DEFAULT_BENEFITS : benefits;
        } catch (Exception ex) {
            return DEFAULT_BENEFITS;
        }
    }

    private void updateCanonicalPlan(String code, BigDecimal price, List<String> benefits) {
        SubscriptionPlanEntity plan = subscriptionPlanRepository.findByCodeIgnoreCaseAndDeletedFalse(code)
                .orElseThrow(() -> new CustomException(
                        409,
                        "Canonical " + code + " subscription plan is missing",
                        HttpStatus.CONFLICT
                ));

        BigDecimal normalizedPrice = price.stripTrailingZeros();
        BillingInterval expectedInterval = "YEARLY".equals(code) ? BillingInterval.YEAR : BillingInterval.MONTH;
        boolean stripePriceChanged = plan.getPrice().compareTo(normalizedPrice) != 0
                || !Objects.equals(plan.getCurrency(), "USD")
                || plan.getBillingInterval() != expectedInterval
                || !Objects.equals(plan.getIntervalCount(), 1);

        plan.setPrice(normalizedPrice);
        plan.setCurrency("USD");
        plan.setBillingInterval(expectedInterval);
        plan.setIntervalCount(1);
        plan.setFeaturesJson(toJson(benefits));
        if (stripePriceChanged) {
            plan.setStripePriceId(null);
        }
        subscriptionPlanRepository.save(plan);
    }

    private String toJson(List<String> benefits) {
        try {
            return objectMapper.writeValueAsString(benefits);
        } catch (Exception ex) {
            throw new CustomException(500, "Failed to save premium benefits", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private record PremiumSnapshot(boolean active, String planCode, LocalDateTime expiresAt) {
        private static PremiumSnapshot inactive(LocalDateTime expiresAt) {
            return new PremiumSnapshot(false, null, expiresAt);
        }
    }
}
