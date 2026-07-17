package com.sep.comiverse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.UpdatePremiumPlanSettingsRequest;
import com.sep.comiverse.dto.response.PremiumPlanSettingsResponse;
import com.sep.comiverse.dto.response.UpgradePlanResponse;
import com.sep.comiverse.dto.response.UserProfileResponse;
import com.sep.comiverse.entity.SystemSettingEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ISystemSettingRepository;
import com.sep.comiverse.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PremiumPlanService {
    private static final String MONTHLY_PRICE_KEY = "premium.monthlyPrice";
    private static final String YEARLY_PRICE_KEY = "premium.yearlyPrice";
    private static final String BENEFITS_KEY = "premium.benefits";
    private static final BigDecimal DEFAULT_MONTHLY_PRICE = new BigDecimal("79000");
    private static final BigDecimal DEFAULT_YEARLY_PRICE = new BigDecimal("790000");
    private static final List<String> DEFAULT_BENEFITS = List.of(
            "Doc khong quang cao khong gioi han",
            "Xem som cac chuong moi nhat",
            "Tai chuong doc offline",
            "Huy hieu Premium doc quyen",
            "Ho tro uu tien"
    );

    private final ISystemSettingRepository settingRepository;
    private final IUserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PremiumPlanSettingsResponse getPremiumPlanSettings() {
        return PremiumPlanSettingsResponse.builder()
                .monthlyPrice(readBigDecimal(MONTHLY_PRICE_KEY, DEFAULT_MONTHLY_PRICE))
                .yearlyPrice(readBigDecimal(YEARLY_PRICE_KEY, DEFAULT_YEARLY_PRICE))
                .benefits(readBenefits())
                .build();
    }

    @Transactional
    public PremiumPlanSettingsResponse updatePremiumPlanSettings(UpdatePremiumPlanSettingsRequest request) {
        List<String> sanitizedBenefits = sanitizeBenefits(request.getBenefits());
        saveSetting(MONTHLY_PRICE_KEY, request.getMonthlyPrice().stripTrailingZeros().toPlainString());
        saveSetting(YEARLY_PRICE_KEY, request.getYearlyPrice().stripTrailingZeros().toPlainString());
        saveSetting(BENEFITS_KEY, toJson(sanitizedBenefits));
        return getPremiumPlanSettings();
    }

    @Transactional
    public UpgradePlanResponse upgradePlan(UUID userId, String rawPlanType) {
        String planType = normalizePlanType(rawPlanType);
        UserEntity user = userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
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

    public boolean hasActivePremium(UserEntity user) {
        return user != null
                && user.getPremiumExpiresAt() != null
                && user.getPremiumExpiresAt().isAfter(LocalDateTime.now());
    }

    public UserProfileResponse toUserProfileResponse(UserEntity user) {
        boolean premiumActive = hasActivePremium(user);
        return UserProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().getRoleName() : "READER")
                .avatarUrl(user.getAvatarUrl())
                .backgroundImageUrl(user.getBackgroundImageUrl())
                .premiumPlan(premiumActive ? user.getPremiumPlan() : null)
                .premiumExpiresAt(user.getPremiumExpiresAt())
                .premiumActive(premiumActive)
                .build();
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
        if (unique.size() > 12) {
            throw new CustomException(400, "Premium benefits can contain at most 12 items", HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(unique);
    }

    private BigDecimal readBigDecimal(String key, BigDecimal fallback) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSettingEntity::getSettingValue)
                .map(value -> {
                    try {
                        return new BigDecimal(value);
                    } catch (NumberFormatException ex) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private List<String> readBenefits() {
        return settingRepository.findBySettingKey(BENEFITS_KEY)
                .map(SystemSettingEntity::getSettingValue)
                .map(value -> {
                    try {
                        List<String> benefits = objectMapper.readValue(value, new TypeReference<List<String>>() {});
                        return benefits == null || benefits.isEmpty() ? DEFAULT_BENEFITS : benefits;
                    } catch (Exception ex) {
                        return DEFAULT_BENEFITS;
                    }
                })
                .orElse(DEFAULT_BENEFITS);
    }

    private void saveSetting(String key, String value) {
        SystemSettingEntity setting = settingRepository.findBySettingKey(key)
                .orElseGet(() -> SystemSettingEntity.builder().settingKey(key).build());
        setting.setSettingValue(value);
        settingRepository.save(setting);
    }

    private String toJson(List<String> benefits) {
        try {
            return objectMapper.writeValueAsString(benefits);
        } catch (Exception ex) {
            throw new CustomException(500, "Failed to save premium benefits", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
