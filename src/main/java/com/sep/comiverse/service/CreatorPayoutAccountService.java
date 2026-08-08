package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep.comiverse.dto.request.CreateStripePayoutOnboardingRequest;
import com.sep.comiverse.dto.response.CreatorPayoutAccountResponse;
import com.sep.comiverse.dto.response.StripePayoutOnboardingLinkResponse;
import com.sep.comiverse.entity.CreatorPayoutAccountEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.StripePayoutProfileStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ICreatorPayoutAccountRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorPayoutAccountService {

    private final ICreatorPayoutAccountRepository profileRepository;
    private final IUserRepository userRepository;
    private final StripeGatewayService stripeGatewayService;
    private final CreatorPayoutSettingsService payoutSettingsService;

    @Value("${stripe.connect.default-country:VN}")
    private String defaultCountry;

    @Transactional(readOnly = true)
    public CreatorPayoutAccountEntity findEntity(UUID userId) {
        if (userId == null) return null;
        return profileRepository.findByUserIdAndDeletedFalse(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public CreatorPayoutAccountResponse getProfile(UserPrincipal principal) {
        UserEntity user = requireCreator(principal);
        return toResponse(findEntity(user.getId()));
    }

    @Transactional
    public StripePayoutOnboardingLinkResponse createOnboardingLink(
            UserPrincipal principal,
            CreateStripePayoutOnboardingRequest request
    ) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);

        if (request == null) {
            throw new CustomException(
                    400,
                    "Payout onboarding request is required",
                    HttpStatus.BAD_REQUEST
            );
        }

        CreatorPayoutSettingsService.ResolvedCurrency requestedCurrency =
                payoutSettingsService.resolveCurrency(request.getPayoutCurrency());

        CreatorPayoutAccountEntity profile = profileRepository
                .findByUserId(user.getId())
                .orElseGet(CreatorPayoutAccountEntity::new);

        String country = normalizeCountry(
                request.getCountryCode(),
                profile.getAccountCountry()
        );

        if (!StringUtils.hasText(profile.getStripeConnectedAccountId())) {
            JsonNode stripeAccount = stripeGatewayService.createPayoutConnectedAccount(
                    user.getId(),
                    user.getEmail(),
                    country,
                    role.name(),
                    requestedCurrency.code()
            );

            profile.setUserId(user.getId());
            profile.setRole(role);
            profile.setStripeConnectedAccountId(requiredText(
                    stripeAccount,
                    "id",
                    "Stripe did not return a connected account ID"
            ));
            profile.setAccountCountry(country);
            profile.setCurrency(requestedCurrency.code());
            profile.setActive(true);
            profile.setDeleted(false);
            applyStripeSnapshot(profile, stripeAccount);
            profile = profileRepository.saveAndFlush(profile);
        } else {
            assertProfileOwner(profile, user.getId());
            profile.setRole(role);
            profile.setActive(true);
            profile.setDeleted(false);

            String existingCurrency = StringUtils.hasText(profile.getCurrency())
                    ? profile.getCurrency().trim().toUpperCase(Locale.ROOT)
                    : "USD";

            if (!existingCurrency.equals(requestedCurrency.code())) {
                if (Boolean.TRUE.equals(profile.getDetailsSubmitted())
                        || isReady(profile)) {
                    throw new CustomException(
                            409,
                            "Payout currency cannot be changed after Stripe onboarding has started. "
                                    + "Use the existing currency or create a new connected account.",
                            HttpStatus.CONFLICT
                    );
                }

                stripeGatewayService.updateConnectedAccountDefaultCurrency(
                        profile.getStripeConnectedAccountId(),
                        requestedCurrency.code()
                );
                profile.setCurrency(requestedCurrency.code());
            }

            profile = syncEntity(profile);
        }

        String pagePath = role == CreatorPayoutRole.AUTHOR
                ? "/author/payout"
                : "/translator/payout";

        JsonNode accountLink =
                stripeGatewayService.createPayoutAccountOnboardingLink(
                        profile.getStripeConnectedAccountId(),
                        pagePath + "?stripe_onboarding=refresh",
                        pagePath + "?stripe_onboarding=return"
                );

        long expiresAt = accountLink == null
                ? 0L
                : accountLink.path("expires_at").asLong(0L);

        return StripePayoutOnboardingLinkResponse.builder()
                .onboardingUrl(requiredText(
                        accountLink,
                        "url",
                        "Stripe did not return an onboarding URL"
                ))
                .expiresAt(expiresAt <= 0
                        ? null
                        : Instant.ofEpochSecond(expiresAt))
                .account(toResponse(profile))
                .build();
    }

    @Transactional
    public CreatorPayoutAccountResponse syncProfile(UserPrincipal principal) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutAccountEntity profile =
                profileRepository.findByUserIdAndDeletedFalse(user.getId())
                        .orElseThrow(() -> new CustomException(
                                404,
                                "Stripe payout profile not found",
                                HttpStatus.NOT_FOUND
                        ));
        assertProfileOwner(profile, user.getId());
        return toResponse(syncEntity(profile));
    }

    @Transactional(readOnly = true)
    public CreatorPayoutAccountEntity requireReadyProfile(UUID userId) {
        CreatorPayoutAccountEntity profile =
                profileRepository.findByUserIdAndDeletedFalse(userId)
                        .orElseThrow(() -> new CustomException(
                                400,
                                "Set up your Stripe payout account first",
                                HttpStatus.BAD_REQUEST
                        ));

        if (!isReady(profile)) {
            throw new CustomException(
                    400,
                    "Complete Stripe onboarding and add a payout method first",
                    HttpStatus.BAD_REQUEST
            );
        }

        payoutSettingsService.resolveCurrency(profile.getCurrency());
        return profile;
    }

    @Transactional
    public void syncFromAccountUpdatedWebhook(JsonNode stripeAccount) {
        if (stripeAccount == null || stripeAccount.isMissingNode()) return;

        String accountId = stripeAccount.path("id").asText("");
        if (!StringUtils.hasText(accountId)) return;

        Optional<CreatorPayoutAccountEntity> existing =
                profileRepository
                        .findByStripeConnectedAccountIdAndDeletedFalse(accountId);
        if (existing.isEmpty()) return;

        CreatorPayoutAccountEntity profile = existing.get();
        validateTestAccountAndOwner(stripeAccount, profile.getUserId());
        applyStripeSnapshot(profile, stripeAccount);
        profileRepository.save(profile);
    }

    public boolean isReady(CreatorPayoutAccountEntity profile) {
        return profile != null
                && Boolean.TRUE.equals(profile.getActive())
                && Boolean.TRUE.equals(profile.getDetailsSubmitted())
                && Boolean.TRUE.equals(profile.getPayoutsEnabled())
                && "active".equalsIgnoreCase(profile.getTransfersCapability())
                && StringUtils.hasText(profile.getExternalAccountLast4());
    }

    public CreatorPayoutAccountResponse toResponse(
            CreatorPayoutAccountEntity profile
    ) {
        if (profile == null) return null;

        return CreatorPayoutAccountResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .role(profile.getRole())
                .stripeConnectedAccountId(profile.getStripeConnectedAccountId())
                .currency(profile.getCurrency())
                .accountCountry(profile.getAccountCountry())
                .transfersCapability(profile.getTransfersCapability())
                .detailsSubmitted(profile.getDetailsSubmitted())
                .chargesEnabled(profile.getChargesEnabled())
                .payoutsEnabled(profile.getPayoutsEnabled())
                .readyForPayout(isReady(profile))
                .active(profile.getActive())
                .onboardingStatus(profile.getOnboardingStatus())
                .requirementsCurrentlyDue(
                        splitRequirements(profile.getRequirementsCurrentlyDue())
                )
                .requirementsDisabledReason(
                        profile.getRequirementsDisabledReason()
                )
                .externalAccountType(profile.getExternalAccountType())
                .externalAccountLast4(profile.getExternalAccountLast4())
                .externalAccountDisplayName(
                        profile.getExternalAccountDisplayName()
                )
                .verifiedAt(profile.getOnboardingCompletedAt())
                .lastSyncedAt(profile.getLastSyncedAt())
                .onboardingCompletedAt(profile.getOnboardingCompletedAt())
                .build();
    }

    private CreatorPayoutAccountEntity syncEntity(
            CreatorPayoutAccountEntity profile
    ) {
        JsonNode stripeAccount =
                stripeGatewayService.retrieveConnectedAccount(
                        profile.getStripeConnectedAccountId()
                );
        validateTestAccountAndOwner(stripeAccount, profile.getUserId());
        applyStripeSnapshot(profile, stripeAccount);
        return profileRepository.save(profile);
    }

    private void applyStripeSnapshot(
            CreatorPayoutAccountEntity profile,
            JsonNode stripeAccount
    ) {
        String country = normalizeCountry(
                stripeAccount.path("country").asText(null),
                profile.getAccountCountry()
        );
        profile.setAccountCountry(country);

        String stripeCurrency = stripeAccount
                .path("default_currency")
                .asText(profile.getCurrency());
        try {
            CreatorPayoutSettingsService.ResolvedCurrency resolved =
                    payoutSettingsService.resolveCurrency(stripeCurrency);
            profile.setCurrency(resolved.code());
        } catch (CustomException ex) {
            if (!StringUtils.hasText(profile.getCurrency())) {
                profile.setCurrency("USD");
            }
        }

        profile.setDetailsSubmitted(
                stripeAccount.path("details_submitted").asBoolean(false)
        );
        profile.setChargesEnabled(
                stripeAccount.path("charges_enabled").asBoolean(false)
        );
        profile.setPayoutsEnabled(
                stripeAccount.path("payouts_enabled").asBoolean(false)
        );
        profile.setTransfersCapability(
                stripeAccount.path("capabilities")
                        .path("transfers")
                        .asText("inactive")
        );

        JsonNode requirements = stripeAccount.path("requirements");
        List<String> currentlyDue =
                jsonTextList(requirements.path("currently_due"));
        profile.setRequirementsCurrentlyDue(String.join(",", currentlyDue));
        profile.setRequirementsDisabledReason(emptyToNull(
                requirements.path("disabled_reason").asText(null)
        ));

        if (stripeAccount.has("external_accounts")) {
            JsonNode externalAccount = firstExternalAccount(stripeAccount);
            if (externalAccount == null) {
                profile.setExternalAccountType(null);
                profile.setExternalAccountLast4(null);
                profile.setExternalAccountDisplayName(null);
            } else {
                String objectType =
                        externalAccount.path("object")
                                .asText("external_account");
                profile.setExternalAccountType(objectType);
                profile.setExternalAccountLast4(emptyToNull(
                        externalAccount.path("last4").asText(null)
                ));

                String displayName =
                        "bank_account".equalsIgnoreCase(objectType)
                                ? externalAccount.path("bank_name")
                                .asText("Bank account")
                                : externalAccount.path("brand")
                                .asText("Debit card");
                profile.setExternalAccountDisplayName(
                        emptyToNull(displayName)
                );
            }
        }

        StripePayoutProfileStatus status;
        if (isReady(profile)) {
            status = StripePayoutProfileStatus.READY;
            if (profile.getOnboardingCompletedAt() == null) {
                profile.setOnboardingCompletedAt(Instant.now());
            }
        } else if (StringUtils.hasText(
                profile.getRequirementsDisabledReason()
        )) {
            status = StripePayoutProfileStatus.RESTRICTED;
        } else if (!currentlyDue.isEmpty()) {
            status = Boolean.TRUE.equals(profile.getDetailsSubmitted())
                    ? StripePayoutProfileStatus.REQUIRES_INFORMATION
                    : StripePayoutProfileStatus.ONBOARDING;
        } else if (Boolean.TRUE.equals(profile.getDetailsSubmitted())) {
            status = StripePayoutProfileStatus.PENDING_VERIFICATION;
        } else {
            status = StripePayoutProfileStatus.CREATED;
        }

        profile.setOnboardingStatus(status);
        profile.setLastSyncedAt(Instant.now());
        profile.setActive(true);
        profile.setDeleted(false);
    }

    private JsonNode firstExternalAccount(JsonNode stripeAccount) {
        JsonNode data = stripeAccount.path("external_accounts").path("data");
        return data.isArray() && data.size() > 0 ? data.get(0) : null;
    }

    private void validateTestAccountAndOwner(
            JsonNode stripeAccount,
            UUID userId
    ) {
        if (stripeAccount == null || stripeAccount.isMissingNode()) {
            throw new CustomException(
                    502,
                    "Stripe account response is empty",
                    HttpStatus.BAD_GATEWAY
            );
        }
        if (stripeAccount.path("livemode").asBoolean(false)) {
            throw new CustomException(
                    400,
                    "Only Stripe sandbox accounts are accepted",
                    HttpStatus.BAD_REQUEST
            );
        }

        String metadataUserId = stripeAccount
                .path("metadata")
                .path("user_id")
                .asText("");
        if (StringUtils.hasText(metadataUserId)
                && !userId.toString().equals(metadataUserId)) {
            throw new CustomException(
                    403,
                    "Stripe payout account belongs to another ComiVerse user",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void assertProfileOwner(
            CreatorPayoutAccountEntity profile,
            UUID userId
    ) {
        if (profile.getUserId() != null
                && !profile.getUserId().equals(userId)) {
            throw new CustomException(
                    403,
                    "Payout profile belongs to another user",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private UserEntity requireCreator(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new CustomException(
                    401,
                    "Authentication is required",
                    HttpStatus.UNAUTHORIZED
            );
        }

        UserEntity user = userRepository.findByIdWithRole(principal.getId())
                .orElseThrow(() -> new CustomException(
                        404,
                        "User not found",
                        HttpStatus.NOT_FOUND
                ));
        resolveCreatorRole(user);
        return user;
    }

    private CreatorPayoutRole resolveCreatorRole(UserEntity user) {
        String roleName =
                user == null || user.getRole() == null
                        ? ""
                        : user.getRole().getRoleName();

        if ("AUTHOR".equalsIgnoreCase(roleName)) {
            return CreatorPayoutRole.AUTHOR;
        }
        if ("TRANSLATOR".equalsIgnoreCase(roleName)) {
            return CreatorPayoutRole.TRANSLATOR;
        }

        throw new CustomException(
                403,
                "Only Author and Translator accounts can configure creator payouts",
                HttpStatus.FORBIDDEN
        );
    }

    private String normalizeCountry(String requested, String existing) {
        if (StringUtils.hasText(requested)
                && requested.trim().matches("^[A-Za-z]{2}$")) {
            return requested.trim().toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(existing)
                && existing.trim().matches("^[A-Za-z]{2}$")) {
            return existing.trim().toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(defaultCountry)
                && defaultCountry.trim().matches("^[A-Za-z]{2}$")) {
            return defaultCountry.trim().toUpperCase(Locale.ROOT);
        }
        return "VN";
    }

    private List<String> jsonTextList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isEmpty()) values.add(value);
            });
        }
        return values;
    }

    private List<String> splitRequirements(String value) {
        if (!StringUtils.hasText(value)) return List.of();

        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String normalized = item.trim();
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    private String requiredText(
            JsonNode node,
            String field,
            String message
    ) {
        String value = node == null
                ? ""
                : node.path(field).asText("");
        if (!StringUtils.hasText(value)) {
            throw new CustomException(
                    502,
                    message,
                    HttpStatus.BAD_GATEWAY
            );
        }
        return value;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
