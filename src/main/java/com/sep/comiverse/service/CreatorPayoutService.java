package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep.comiverse.dto.request.CreatePayoutRequest;
import com.sep.comiverse.dto.request.LinkPayoutAccountRequest;
import com.sep.comiverse.dto.response.AdminPayoutPageResponse;
import com.sep.comiverse.dto.response.CreatorPayoutAccountResponse;
import com.sep.comiverse.dto.response.CreatorPayoutOverviewResponse;
import com.sep.comiverse.dto.response.CreatorPayoutRequestResponse;
import com.sep.comiverse.entity.ComicMetricSnapshotEntity;
import com.sep.comiverse.entity.CreatorPayoutAccountEntity;
import com.sep.comiverse.entity.CreatorPayoutRequestEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IChapterTranslationRepository;
import com.sep.comiverse.repository.IComicMetricSnapshotRepository;
import com.sep.comiverse.repository.ICreatorPayoutAccountRepository;
import com.sep.comiverse.repository.ICreatorPayoutRequestRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorPayoutService {

    private static final int MAX_ADMIN_PAGE_SIZE = 100;

    private final ICreatorPayoutAccountRepository payoutAccountRepository;
    private final ICreatorPayoutRequestRepository payoutRequestRepository;
    private final IComicMetricSnapshotRepository comicMetricSnapshotRepository;
    private final IChapterTranslationRepository chapterTranslationRepository;
    private final IUserRepository userRepository;
    private final StripeGatewayService stripeGatewayService;

    @Value("${payout.currency:VND}")
    private String payoutCurrency;

    @Value("${payout.minimum-amount:50000}")
    private BigDecimal minimumPayoutAmount;

    @Value("${payout.translator.rate-per-translation:50000}")
    private BigDecimal translatorRatePerTranslation;

    @Transactional(readOnly = true)
    public CreatorPayoutOverviewResponse getOverview(UserPrincipal principal, String requestedMonth) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);
        YearMonth selectedMonth = resolveMonth(requestedMonth);
        MonthlyCalculation calculation = calculateMonthlyAmount(user.getId(), role, selectedMonth);

        List<CreatorPayoutRequestEntity> entities = payoutRequestRepository
                .findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(user.getId());
        CreatorPayoutRequestEntity existing = entities.stream()
                .filter(item -> selectedMonth.toString().equals(item.getPayoutMonth()))
                .findFirst()
                .orElse(null);
        CreatorPayoutAccountEntity account = payoutAccountRepository
                .findByUserIdAndDeletedFalse(user.getId())
                .orElse(null);

        BigDecimal lifetimePaid = sumByStatuses(entities, List.of(CreatorPayoutStatus.PAID));
        BigDecimal pending = sumByStatuses(
                entities,
                List.of(CreatorPayoutStatus.PENDING, CreatorPayoutStatus.APPROVED, CreatorPayoutStatus.PROCESSING)
        );

        Requestability requestability = evaluateRequestability(account, existing, selectedMonth, calculation.amount());

        return CreatorPayoutOverviewResponse.builder()
                .role(role)
                .selectedMonth(selectedMonth.toString())
                .lastClosedMonth(lastClosedMonth().toString())
                .monthlyGrossAmount(calculation.amount())
                .minimumPayoutAmount(normalizeAmount(minimumPayoutAmount))
                .lifetimePaidAmount(lifetimePaid)
                .pendingAmount(pending)
                .requestable(requestability.requestable())
                .notRequestableReason(requestability.reason())
                .calculationUnitCount(calculation.unitCount())
                .calculationUnitLabel(calculation.unitLabel())
                .calculationUnitRate(calculation.unitRate())
                .account(toAccountResponse(account))
                .existingRequest(toRequestResponse(existing))
                .requests(entities.stream().map(this::toRequestResponse).toList())
                .build();
    }

    @Transactional
    public CreatorPayoutAccountResponse linkPayoutAccount(
            UserPrincipal principal,
            LinkPayoutAccountRequest request
    ) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);
        String connectedAccountId = request.getStripeConnectedAccountId().trim();
        payoutAccountRepository.findByStripeConnectedAccountIdAndDeletedFalse(connectedAccountId)
                .filter(existing -> !user.getId().equals(existing.getUserId()))
                .ifPresent(existing -> {
                    throw new CustomException(
                            409,
                            "Stripe connected account is already linked to another ComiVerse user",
                            HttpStatus.CONFLICT
                    );
                });
        VerifiedStripeAccount verified = verifyConnectedAccountForUser(connectedAccountId, user.getId());

        CreatorPayoutAccountEntity account = payoutAccountRepository
                .findByUserIdAndDeletedFalse(user.getId())
                .orElseGet(CreatorPayoutAccountEntity::new);
        account.setUserId(user.getId());
        account.setRole(role);
        account.setStripeConnectedAccountId(connectedAccountId);
        account.setCurrency(normalizedCurrency());
        account.setAccountCountry(verified.country());
        account.setTransfersCapability(verified.transfersCapability());
        account.setPayoutsEnabled(verified.payoutsEnabled());
        account.setVerifiedAt(Instant.now());
        account.setActive(true);
        account.setDeleted(false);
        return toAccountResponse(payoutAccountRepository.save(account));
    }

    @Transactional
    public CreatorPayoutRequestResponse createRequest(
            UserPrincipal principal,
            CreatePayoutRequest request
    ) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);
        YearMonth payoutMonth = parseMonth(request.getPayoutMonth());
        ensureClosedMonth(payoutMonth);

        CreatorPayoutAccountEntity account = payoutAccountRepository
                .findByUserIdAndDeletedFalse(user.getId())
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new CustomException(
                        400,
                        "Link a verified Stripe connected account before requesting payout",
                        HttpStatus.BAD_REQUEST
                ));

        MonthlyCalculation calculation = calculateMonthlyAmount(user.getId(), role, payoutMonth);
        if (calculation.amount().compareTo(normalizeAmount(minimumPayoutAmount)) < 0) {
            throw new CustomException(
                    400,
                    "Monthly earnings are below the minimum payout amount of "
                            + normalizeAmount(minimumPayoutAmount).toPlainString()
                            + " " + normalizedCurrency(),
                    HttpStatus.BAD_REQUEST
            );
        }

        CreatorPayoutRequestEntity payout = payoutRequestRepository
                .findByUserIdAndPayoutMonthAndDeletedFalse(user.getId(), payoutMonth.toString())
                .orElseGet(CreatorPayoutRequestEntity::new);

        if (payout.getId() != null && !isRetryable(payout.getStatus())) {
            throw new CustomException(
                    409,
                    "A payout request already exists for " + payoutMonth,
                    HttpStatus.CONFLICT
            );
        }

        payout.setUserId(user.getId());
        payout.setUserName(displayName(user));
        payout.setUserEmail(user.getEmail());
        payout.setRole(role);
        payout.setPayoutMonth(payoutMonth.toString());
        payout.setAmount(calculation.amount());
        payout.setCurrency(normalizedCurrency());
        payout.setStatus(CreatorPayoutStatus.PENDING);
        payout.setStripeConnectedAccountId(account.getStripeConnectedAccountId());
        payout.setStripeTransferId(null);
        payout.setRequestNote(trimToNull(request.getNote()));
        payout.setAdminNote(null);
        payout.setFailureReason(null);
        payout.setCalculationDetails(calculation.details());
        payout.setRequestedAt(Instant.now());
        payout.setApprovedAt(null);
        payout.setPaidAt(null);
        payout.setRejectedAt(null);
        payout.setFailedAt(null);
        payout.setDeleted(false);
        return toRequestResponse(payoutRequestRepository.save(payout));
    }

    @Transactional(readOnly = true)
    public AdminPayoutPageResponse getAdminPayouts(
            CreatorPayoutStatus status,
            int page,
            int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_ADMIN_PAGE_SIZE, size));
        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<CreatorPayoutRequestEntity> result = status == null
                ? payoutRequestRepository.findAll(pageable)
                : payoutRequestRepository.findAllByStatusAndDeletedFalse(status, pageable);

        List<CreatorPayoutRequestEntity> all = payoutRequestRepository.findAll();
        Map<CreatorPayoutStatus, Long> counts = new EnumMap<>(CreatorPayoutStatus.class);
        Map<CreatorPayoutStatus, BigDecimal> totals = new EnumMap<>(CreatorPayoutStatus.class);
        for (CreatorPayoutStatus itemStatus : CreatorPayoutStatus.values()) {
            List<CreatorPayoutRequestEntity> matching = all.stream()
                    .filter(item -> item.getStatus() == itemStatus)
                    .toList();
            counts.put(itemStatus, (long) matching.size());
            totals.put(itemStatus, sumAmounts(matching));
        }

        return AdminPayoutPageResponse.builder()
                .items(result.getContent().stream().map(this::toRequestResponse).toList())
                .counts(counts)
                .totals(totals)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public CreatorPayoutRequestResponse approve(UUID payoutId, String adminNote) {
        CreatorPayoutRequestEntity payout = getLockedPayout(payoutId);
        if (payout.getStatus() != CreatorPayoutStatus.PENDING) {
            throw invalidTransition(payout, "approve");
        }

        CreatorPayoutAccountEntity account = payoutAccountRepository
                .findByUserIdAndDeletedFalse(payout.getUserId())
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new CustomException(
                        400,
                        "Creator payout account is no longer active",
                        HttpStatus.BAD_REQUEST
                ));
        verifyConnectedAccountForUser(account.getStripeConnectedAccountId(), payout.getUserId());

        payout.setStripeConnectedAccountId(account.getStripeConnectedAccountId());
        payout.setStatus(CreatorPayoutStatus.APPROVED);
        payout.setApprovedAt(Instant.now());
        payout.setAdminNote(trimToNull(adminNote));
        payout.setFailureReason(null);
        payout.setFailedAt(null);
        return toRequestResponse(payoutRequestRepository.save(payout));
    }

    @Transactional
    public CreatorPayoutRequestResponse reject(UUID payoutId, String reason) {
        CreatorPayoutRequestEntity payout = getLockedPayout(payoutId);
        if (payout.getStatus() != CreatorPayoutStatus.PENDING
                && payout.getStatus() != CreatorPayoutStatus.APPROVED) {
            throw invalidTransition(payout, "reject");
        }
        payout.setStatus(CreatorPayoutStatus.REJECTED);
        payout.setAdminNote(trimToNull(reason));
        payout.setRejectedAt(Instant.now());
        payout.setFailureReason(null);
        return toRequestResponse(payoutRequestRepository.save(payout));
    }

    @Transactional(noRollbackFor = CustomException.class)
    public CreatorPayoutRequestResponse payWithStripe(UUID payoutId) {
        CreatorPayoutRequestEntity payout = getLockedPayout(payoutId);
        if (payout.getStatus() == CreatorPayoutStatus.PAID && StringUtils.hasText(payout.getStripeTransferId())) {
            return toRequestResponse(payout);
        }
        if (payout.getStatus() != CreatorPayoutStatus.APPROVED
                && payout.getStatus() != CreatorPayoutStatus.FAILED) {
            throw invalidTransition(payout, "pay");
        }

        payout.setStatus(CreatorPayoutStatus.PROCESSING);
        payout.setFailureReason(null);
        payout.setFailedAt(null);
        payoutRequestRepository.saveAndFlush(payout);

        try {
            JsonNode transfer = stripeGatewayService.createTransfer(
                    payout.getStripeConnectedAccountId(),
                    payout.getAmount(),
                    payout.getCurrency(),
                    payout.getId(),
                    payout.getUserId(),
                    payout.getPayoutMonth()
            );
            String transferId = transfer == null ? null : transfer.path("id").asText(null);
            if (!StringUtils.hasText(transferId)) {
                throw new CustomException(502, "Stripe did not return a transfer ID", HttpStatus.BAD_GATEWAY);
            }
            payout.setStripeTransferId(transferId);
            payout.setStatus(CreatorPayoutStatus.PAID);
            payout.setPaidAt(Instant.now());
            payout.setFailureReason(null);
            return toRequestResponse(payoutRequestRepository.save(payout));
        } catch (CustomException ex) {
            payout.setStatus(CreatorPayoutStatus.FAILED);
            payout.setFailureReason(ex.getMessage());
            payout.setFailedAt(Instant.now());
            payoutRequestRepository.save(payout);
            throw ex;
        }
    }

    private MonthlyCalculation calculateMonthlyAmount(
            UUID userId,
            CreatorPayoutRole role,
            YearMonth month
    ) {
        Instant from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        if (role == CreatorPayoutRole.AUTHOR) {
            Map<UUID, ComicMetricSnapshotEntity> latestByComic = new LinkedHashMap<>();
            comicMetricSnapshotRepository.findAllByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                    .filter(snapshot -> snapshot.getCreatedAt() != null)
                    .filter(snapshot -> !snapshot.getCreatedAt().isBefore(from) && snapshot.getCreatedAt().isBefore(to))
                    .forEach(snapshot -> latestByComic.putIfAbsent(snapshot.getComicId(), snapshot));
            BigDecimal amount = latestByComic.values().stream()
                    .map(ComicMetricSnapshotEntity::getEstimatedRevenue)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal normalized = normalizeAmount(amount);
            return new MonthlyCalculation(
                    normalized,
                    (long) latestByComic.size(),
                    "comics with monthly revenue snapshots",
                    null,
                    "Latest monthly revenue snapshot per comic: " + latestByComic.size()
            );
        }

        long recordedTranslations = chapterTranslationRepository
                .countTranslationsForUserInPeriod(userId, from, to);
        BigDecimal rate = normalizeAmount(translatorRatePerTranslation);
        BigDecimal amount = normalizeAmount(rate.multiply(BigDecimal.valueOf(recordedTranslations)));
        return new MonthlyCalculation(
                amount,
                recordedTranslations,
                "chapter translations recorded",
                rate,
                recordedTranslations + " chapter translations recorded x "
                        + rate.toPlainString() + " " + normalizedCurrency()
        );
    }

    private Requestability evaluateRequestability(
            CreatorPayoutAccountEntity account,
            CreatorPayoutRequestEntity existing,
            YearMonth selectedMonth,
            BigDecimal amount
    ) {
        if (selectedMonth.isAfter(lastClosedMonth())) {
            return new Requestability(false, "Only completed months can be requested");
        }
        if (account == null || !Boolean.TRUE.equals(account.getActive())) {
            return new Requestability(false, "Link a Stripe connected account first");
        }
        if (existing != null && !isRetryable(existing.getStatus())) {
            return new Requestability(false, "A payout request already exists for this month");
        }
        if (amount.compareTo(normalizeAmount(minimumPayoutAmount)) < 0) {
            return new Requestability(false, "Monthly earnings are below the minimum payout amount");
        }
        return new Requestability(true, null);
    }

    private VerifiedStripeAccount verifyConnectedAccountForUser(String accountId, UUID userId) {
        JsonNode account = stripeGatewayService.retrieveConnectedAccount(accountId);
        if (account == null || account.path("livemode").asBoolean(false)) {
            throw new CustomException(
                    400,
                    "Only Stripe sandbox connected accounts are accepted",
                    HttpStatus.BAD_REQUEST
            );
        }

        String metadataUserId = account.path("metadata").path("user_id").asText("");
        if (StringUtils.hasText(metadataUserId) && !userId.toString().equals(metadataUserId)) {
            throw new CustomException(
                    403,
                    "Stripe connected account belongs to another ComiVerse user",
                    HttpStatus.FORBIDDEN
            );
        }

        String transferCapability = account.path("capabilities").path("transfers").asText("unknown");
        if ("inactive".equalsIgnoreCase(transferCapability)) {
            throw new CustomException(
                    400,
                    "Stripe connected account does not have the transfers capability",
                    HttpStatus.BAD_REQUEST
            );
        }
        return new VerifiedStripeAccount(
                account.path("country").asText(null),
                transferCapability,
                account.path("payouts_enabled").asBoolean(false)
        );
    }

    private CreatorPayoutRequestEntity getLockedPayout(UUID payoutId) {
        if (payoutId == null) {
            throw new CustomException(400, "Payout request ID is required", HttpStatus.BAD_REQUEST);
        }
        return payoutRequestRepository.findLockedById(payoutId)
                .orElseThrow(() -> new CustomException(404, "Payout request not found", HttpStatus.NOT_FOUND));
    }

    private CustomException invalidTransition(CreatorPayoutRequestEntity payout, String action) {
        return new CustomException(
                409,
                "Cannot " + action + " payout while status is " + payout.getStatus(),
                HttpStatus.CONFLICT
        );
    }

    private UserEntity requireCreator(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new CustomException(401, "Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        UserEntity user = userRepository.findByIdWithRole(principal.getId())
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        resolveCreatorRole(user);
        return user;
    }

    private CreatorPayoutRole resolveCreatorRole(UserEntity user) {
        String roleName = user == null || user.getRole() == null ? "" : user.getRole().getRoleName();
        if ("AUTHOR".equalsIgnoreCase(roleName)) {
            return CreatorPayoutRole.AUTHOR;
        }
        if ("TRANSLATOR".equalsIgnoreCase(roleName)) {
            return CreatorPayoutRole.TRANSLATOR;
        }
        throw new CustomException(
                403,
                "Only Author and Translator accounts can request creator payouts",
                HttpStatus.FORBIDDEN
        );
    }

    private YearMonth resolveMonth(String value) {
        return StringUtils.hasText(value) ? parseMonth(value) : lastClosedMonth();
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException ex) {
            throw new CustomException(400, "Payout month must use YYYY-MM format", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureClosedMonth(YearMonth month) {
        if (month.isAfter(lastClosedMonth())) {
            throw new CustomException(
                    400,
                    "Payout requests are available only after the month is closed",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private YearMonth lastClosedMonth() {
        return YearMonth.now(ZoneOffset.UTC).minusMonths(1);
    }

    private boolean isRetryable(CreatorPayoutStatus status) {
        return status == CreatorPayoutStatus.REJECTED || status == CreatorPayoutStatus.FAILED;
    }

    private BigDecimal sumByStatuses(
            List<CreatorPayoutRequestEntity> entities,
            List<CreatorPayoutStatus> statuses
    ) {
        return sumAmounts(entities.stream().filter(item -> statuses.contains(item.getStatus())).toList());
    }

    private BigDecimal sumAmounts(List<CreatorPayoutRequestEntity> entities) {
        return normalizeAmount(entities.stream()
                .map(CreatorPayoutRequestEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "VND".equals(normalizedCurrency())
                ? safe.setScale(0, RoundingMode.HALF_UP)
                : safe.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizedCurrency() {
        String value = StringUtils.hasText(payoutCurrency) ? payoutCurrency.trim().toUpperCase(Locale.ROOT) : "VND";
        if (!value.matches("^[A-Z]{3}$")) {
            return "VND";
        }
        return value;
    }

    private String displayName(UserEntity user) {
        if (StringUtils.hasText(user.getFullName())) {
            return user.getFullName().trim();
        }
        return user.getUsername();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private CreatorPayoutAccountResponse toAccountResponse(CreatorPayoutAccountEntity account) {
        if (account == null) {
            return null;
        }
        return CreatorPayoutAccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .role(account.getRole())
                .stripeConnectedAccountId(account.getStripeConnectedAccountId())
                .currency(account.getCurrency())
                .accountCountry(account.getAccountCountry())
                .transfersCapability(account.getTransfersCapability())
                .payoutsEnabled(account.getPayoutsEnabled())
                .active(account.getActive())
                .verifiedAt(account.getVerifiedAt())
                .build();
    }

    private CreatorPayoutRequestResponse toRequestResponse(CreatorPayoutRequestEntity payout) {
        if (payout == null) {
            return null;
        }
        return CreatorPayoutRequestResponse.builder()
                .id(payout.getId())
                .userId(payout.getUserId())
                .userName(payout.getUserName())
                .userEmail(payout.getUserEmail())
                .role(payout.getRole())
                .payoutMonth(payout.getPayoutMonth())
                .amount(payout.getAmount())
                .currency(payout.getCurrency())
                .status(payout.getStatus())
                .stripeConnectedAccountId(payout.getStripeConnectedAccountId())
                .stripeTransferId(payout.getStripeTransferId())
                .requestNote(payout.getRequestNote())
                .adminNote(payout.getAdminNote())
                .failureReason(payout.getFailureReason())
                .calculationDetails(payout.getCalculationDetails())
                .requestedAt(payout.getRequestedAt())
                .approvedAt(payout.getApprovedAt())
                .paidAt(payout.getPaidAt())
                .rejectedAt(payout.getRejectedAt())
                .failedAt(payout.getFailedAt())
                .createdAt(payout.getCreatedAt())
                .updatedAt(payout.getUpdatedAt())
                .build();
    }

    private record MonthlyCalculation(
            BigDecimal amount,
            Long unitCount,
            String unitLabel,
            BigDecimal unitRate,
            String details
    ) {
    }

    private record Requestability(boolean requestable, String reason) {
    }

    private record VerifiedStripeAccount(
            String country,
            String transfersCapability,
            Boolean payoutsEnabled
    ) {
    }
}
