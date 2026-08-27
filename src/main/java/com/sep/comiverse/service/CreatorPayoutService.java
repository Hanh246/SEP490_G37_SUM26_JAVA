package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep.comiverse.dto.request.CreatePayoutRequest;
import com.sep.comiverse.dto.response.AdminPayoutPageResponse;
import com.sep.comiverse.dto.response.AuthorComicRevenueResponse;
import com.sep.comiverse.dto.response.CreatorPayoutOverviewResponse;
import com.sep.comiverse.dto.response.CreatorPayoutRequestResponse;
import com.sep.comiverse.dto.response.TranslatorTaskRevenueResponse;
import com.sep.comiverse.entity.CreatorPayoutRequestEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.CreatorPayoutAccountEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.TranslatorChapterSettlementEntity;
import com.sep.comiverse.entity.TranslatorEarningEntryEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutCurrency;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import com.sep.comiverse.entity.enums.TranslatorEarningEntryType;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ICreatorPayoutRequestRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.ITranslatorChapterSettlementRepository;
import com.sep.comiverse.repository.ITranslatorEarningEntryRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreatorPayoutService {

    private static final int MAX_ADMIN_PAGE_SIZE = 100;

    @Value("${payout.allow-current-month:true}")
    private boolean allowCurrentMonth;

    @Value("${payout.time-zone:Asia/Ho_Chi_Minh}")
    private String payoutTimeZone;

    private final CreatorPayoutAccountService payoutProfileService;
    private final ICreatorPayoutRequestRepository payoutRequestRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final ITranslatorChapterSettlementRepository translatorSettlementRepository;
    private final ITranslatorEarningEntryRepository translatorEarningRepository;
    private final IUserRepository userRepository;
    private final StripeGatewayService stripeGatewayService;
    private final CreatorPayoutSettingsService payoutSettingsService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public CreatorPayoutOverviewResponse getOverview(
            UserPrincipal principal,
            String requestedMonth
    ) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);
        YearMonth selectedMonth = resolveMonth(requestedMonth);
        CreatorPayoutSettingEntity settings = payoutSettingsService.currentSettings();

        CreatorPayoutAccountEntity account =
                payoutProfileService.findEntity(user.getId());
        CreatorPayoutSettingsService.ResolvedCurrency currency =
                payoutSettingsService.resolveCurrency(
                        account == null ? "USD" : account.getCurrency()
                );

        MonthlyCalculation calculation =
                calculateMonthlyAmount(user.getId(), role, selectedMonth, settings);

        List<CreatorPayoutRequestEntity> entities = payoutRequestRepository
                .findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(user.getId());
        List<CreatorPayoutRequestEntity> roleEntities = entities.stream()
                .filter(item -> item.getRole() == role)
                .toList();
        CreatorPayoutRequestEntity existing = roleEntities.stream()
                .filter(item -> selectedMonth.toString().equals(item.getPayoutMonth()))
                .findFirst()
                .orElse(null);
        CreatorPayoutRequestEntity activeRequest = roleEntities.stream()
                .filter(item -> selectedMonth.toString().equals(item.getPayoutMonth()))
                .filter(item -> isActiveReservation(item.getStatus()))
                .findFirst()
                .orElse(null);

        BigDecimal lifetimePaidUsd = sumBaseUsdByStatuses(
                roleEntities,
                List.of(CreatorPayoutStatus.PAID)
        );
        BigDecimal pendingUsd = sumBaseUsdByStatuses(
                roleEntities,
                List.of(
                        CreatorPayoutStatus.PENDING,
                        CreatorPayoutStatus.APPROVED,
                        CreatorPayoutStatus.PROCESSING
                )
        );
        BigDecimal minimumUsd =
                normalizeUsd(settings.getMinimumPayoutUsd());
        Requestability requestability = evaluateRequestability(
                account,
                activeRequest,
                selectedMonth,
                calculation.withdrawableUsd(),
                minimumUsd
        );
        BigDecimal overLimitUsd = role == CreatorPayoutRole.TRANSLATOR
                ? BigDecimal.ZERO.setScale(2)
                : normalizeUsd(
                calculation.grossUsd()
                        .subtract(calculation.grossUsd().min(calculation.monthlyLimitUsd()))
                        .max(BigDecimal.ZERO)
        );

        return CreatorPayoutOverviewResponse.builder()
                .role(role)
                .selectedMonth(selectedMonth.toString())
                .lastClosedMonth(lastClosedMonth().toString())
                .latestRequestableMonth(latestRequestableMonth().toString())
                .currentMonthAllowed(allowCurrentMonth)
                .monthlyGrossAmountUsd(calculation.grossUsd())
                .monthlyWithdrawableAmountUsd(calculation.withdrawableUsd())
                .monthlyOverLimitAmountUsd(overLimitUsd)
                .monthlyLimitAmountUsd(calculation.monthlyLimitUsd())
                .minimumPayoutAmountUsd(minimumUsd)
                .lifetimePaidAmountUsd(lifetimePaidUsd)
                .pendingAmountUsd(pendingUsd)
                .availableBalanceAmountUsd(calculation.availableBalanceUsd())
                .cumulativeEarnedAmountUsd(calculation.cumulativeEarnedUsd())
                .pendingCurrentMonthAmountUsd(calculation.pendingCurrentMonthUsd())
                .monthlyGrossAmount(convert(calculation.grossUsd(), currency))
                .monthlyWithdrawableAmount(convert(calculation.withdrawableUsd(), currency))
                .monthlyOverLimitAmount(convert(overLimitUsd, currency))
                .monthlyLimitAmount(convert(calculation.monthlyLimitUsd(), currency))
                .minimumPayoutAmount(convert(minimumUsd, currency))
                .lifetimePaidAmount(convert(lifetimePaidUsd, currency))
                .pendingAmount(convert(pendingUsd, currency))
                .availableBalanceAmount(convert(calculation.availableBalanceUsd(), currency))
                .cumulativeEarnedAmount(convert(calculation.cumulativeEarnedUsd(), currency))
                .pendingCurrentMonthAmount(convert(calculation.pendingCurrentMonthUsd(), currency))
                .payoutCurrency(currency.code())
                .payoutCurrencySymbol(currency.symbol())
                .payoutUnitsPerUsd(currency.unitsPerUsd())
                .accountCountry(account == null ? null : account.getAccountCountry())
                .supportedCurrencies(payoutSettingsService.getSupportedCurrencies())
                .requestable(requestability.requestable())
                .notRequestableReason(requestability.reason())
                .calculationUnitCount(calculation.unitCount())
                .calculationUnitLabel(calculation.unitLabel())
                .calculationUnitRateUsd(calculation.unitRateUsd())
                .calculationUnitRate(
                        calculation.unitRateUsd() == null
                                ? null
                                : convert(calculation.unitRateUsd(), currency)
                )
                .translatorTaskRateUsd(
                        normalizeUsd(settings.getTranslatorTaskRateUsd())
                )
                .translatorPageRateUsd(
                        normalizeUsd(settings.getTranslatorTaskRateUsd())
                )
                .authorViewsPerUnit(settings.getAuthorViewsPerUnit())
                .authorViewUnitRateUsd(
                        normalizeUsd(settings.getAuthorViewUnitRateUsd())
                )
                .authorFollowsPerUnit(settings.getAuthorFollowsPerUnit())
                .authorFollowUnitRateUsd(
                        normalizeUsd(settings.getAuthorFollowUnitRateUsd())
                )
                .calculationPolicy(calculation.policy())
                .translatorTasks(calculation.translatorTasks())
                .authorComics(calculation.authorComics())
                .account(payoutProfileService.toResponse(account))
                .existingRequest(toRequestResponse(existing))
                .requests(entities.stream().map(this::toRequestResponse).toList())
                .build();
    }

    @Transactional
    public CreatorPayoutRequestResponse createRequest(
            UserPrincipal principal,
            CreatePayoutRequest request
    ) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);
        YearMonth payoutMonth = parseMonth(request.getPayoutMonth());
        ensureRequestableMonth(payoutMonth);

        CreatorPayoutAccountEntity account =
                payoutProfileService.requireReadyProfile(user.getId());
        CreatorPayoutSettingsService.ResolvedCurrency currency =
                payoutSettingsService.resolveCurrency(account.getCurrency());

        if (StringUtils.hasText(request.getPayoutCurrency())
                && !currency.code().equalsIgnoreCase(request.getPayoutCurrency())) {
            throw new CustomException(
                    400,
                    "Requested currency must match the Stripe payout account currency: "
                            + currency.code(),
                    HttpStatus.BAD_REQUEST
            );
        }

        CreatorPayoutSettingEntity settings =
                payoutSettingsService.currentSettings();
        MonthlyCalculation calculation =
                calculateMonthlyAmount(user.getId(), role, payoutMonth, settings);
        BigDecimal minimumUsd =
                normalizeUsd(settings.getMinimumPayoutUsd());

        BigDecimal requestedAmount =
                payoutSettingsService.normalizeMoney(request.getRequestedAmount());
        if (requestedAmount.signum() <= 0) {
            throw new CustomException(
                    400,
                    "Requested amount must be greater than zero",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal requestedUsd =
                payoutSettingsService.convertCurrencyToUsd(
                        requestedAmount,
                        currency
                );

        if (requestedUsd.compareTo(minimumUsd) < 0) {
            throw new CustomException(
                    400,
                    "Requested amount is below the minimum payout amount of "
                            + currency.symbol()
                            + convert(minimumUsd, currency).toPlainString()
                            + " " + currency.code(),
                    HttpStatus.BAD_REQUEST
            );
        }
        if (requestedUsd.compareTo(calculation.withdrawableUsd()) > 0) {
            throw new CustomException(
                    400,
                    "Requested amount exceeds the withdrawable amount for "
                            + payoutMonth,
                    HttpStatus.BAD_REQUEST
            );
        }

        boolean activeRequestExists = payoutRequestRepository
                .findAllByUserIdAndPayoutMonthAndDeletedFalseOrderByCreatedAtDesc(
                        user.getId(),
                        payoutMonth.toString()
                )
                .stream()
                .filter(item -> item.getRole() == role)
                .anyMatch(item -> isActiveReservation(item.getStatus()));
        if (activeRequestExists) {
            throw new CustomException(
                    409,
                    "Another payout request is still pending for " + payoutMonth,
                    HttpStatus.CONFLICT
            );
        }

        CreatorPayoutRequestEntity payout = new CreatorPayoutRequestEntity();

        payout.setUserId(user.getId());
        payout.setUserName(displayName(user));
        payout.setUserEmail(user.getEmail());
        payout.setRole(role);
        payout.setPayoutMonth(payoutMonth.toString());
        payout.setAmount(requestedAmount);
        payout.setGrossAmountUsd(calculation.grossUsd());
        payout.setBaseAmountUsd(requestedUsd);
        payout.setMonthlyLimitUsd(calculation.monthlyLimitUsd());
        payout.setPayoutUnitsPerUsd(currency.unitsPerUsd());
        payout.setAccountCountry(
                normalizeCountryOrDefault(account.getAccountCountry())
        );
        payout.setCurrency(currency.code());
        payout.setStatus(CreatorPayoutStatus.PENDING);
        payout.setStripeConnectedAccountId(
                account.getStripeConnectedAccountId()
        );
        payout.setStripeTransferId(null);
        payout.setRequestNote(trimToNull(request.getNote()));
        payout.setAdminNote(null);
        payout.setFailureReason(null);
        payout.setCalculationDetails(
                calculation.details()
                        + "; requested=" + currency.symbol()
                        + requestedAmount.toPlainString()
                        + " " + currency.code()
                        + "; requestedUsd=$" + requestedUsd.toPlainString()
                        + "; availableUsd=$"
                        + calculation.withdrawableUsd().toPlainString()
                        + "; unitsPerUsd="
                        + currency.unitsPerUsd().toPlainString()
        );
        payout.setRequestedAt(Instant.now());
        payout.setApprovedAt(null);
        payout.setPaidAt(null);
        payout.setRejectedAt(null);
        payout.setFailedAt(null);
        payout.setDeleted(false);

        return toRequestResponse(payoutRequestRepository.save(payout));
    }

    @Transactional(readOnly = true)
    public AdminPayoutPageResponse getAdminPayouts(CreatorPayoutStatus status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_ADMIN_PAGE_SIZE, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
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
            totals.put(itemStatus, sumBaseUsd(matching));
        }

        return AdminPayoutPageResponse.builder()
                .items(result.getContent().stream().map(this::toRequestResponse).toList())
                .counts(counts)
                .totals(totals)
                .totalsCurrency("USD")
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public CreatorPayoutRequestResponse approve(
            UUID payoutId,
            String adminNote
    ) {
        CreatorPayoutRequestEntity payout = getLockedPayout(payoutId);
        if (payout.getStatus() != CreatorPayoutStatus.PENDING) {
            throw invalidTransition(payout, "approve");
        }

        CreatorPayoutAccountEntity account =
                payoutProfileService.requireReadyProfile(payout.getUserId());

        if (!payout.getCurrency().equalsIgnoreCase(account.getCurrency())) {
            throw new CustomException(
                    409,
                    "Stripe payout account currency changed after the request was created",
                    HttpStatus.CONFLICT
            );
        }

        payout.setStripeConnectedAccountId(
                account.getStripeConnectedAccountId()
        );
        payout.setAccountCountry(
                normalizeCountryOrDefault(account.getAccountCountry())
        );
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

        if (payout.getStatus() == CreatorPayoutStatus.PAID
                && StringUtils.hasText(payout.getStripeTransferId())) {
            return toRequestResponse(payout);
        }
        if (payout.getStatus() != CreatorPayoutStatus.APPROVED
                && payout.getStatus() != CreatorPayoutStatus.FAILED) {
            throw invalidTransition(payout, "pay");
        }

        CreatorPayoutAccountEntity account =
                payoutProfileService.requireReadyProfile(payout.getUserId());
        if (!payout.getCurrency().equalsIgnoreCase(account.getCurrency())) {
            throw new CustomException(
                    409,
                    "Stripe payout account currency does not match the approved request",
                    HttpStatus.CONFLICT
            );
        }

        CreatorPayoutSettingsService.ResolvedCurrency currency =
                payoutSettingsService.resolveCurrency(payout.getCurrency());
        payout.setStripeConnectedAccountId(
                account.getStripeConnectedAccountId()
        );
        payout.setAccountCountry(
                normalizeCountryOrDefault(account.getAccountCountry())
        );

        if (payout.getAmount() == null || payout.getAmount().signum() <= 0) {
            payout.setAmount(
                    convert(
                            payout.getBaseAmountUsd(),
                            currency
                    )
            );
        }
        if (payout.getPayoutUnitsPerUsd() == null) {
            payout.setPayoutUnitsPerUsd(currency.unitsPerUsd());
        }
        payout.setStatus(CreatorPayoutStatus.PROCESSING);
        payout.setFailureReason(null);
        payout.setFailedAt(null);
        payoutRequestRepository.saveAndFlush(payout);

        try {
            JsonNode transfer = stripeGatewayService.createTransfer(
                    payout.getStripeConnectedAccountId(),
                    payout.getAmount(),
                    currency.code(),
                    payout.getId(),
                    payout.getUserId(),
                    payout.getPayoutMonth()
            );

            String transferId = transfer == null
                    ? null
                    : transfer.path("id").asText(null);
            if (!StringUtils.hasText(transferId)) {
                throw new CustomException(
                        502,
                        "Stripe did not return a transfer ID",
                        HttpStatus.BAD_GATEWAY
                );
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
        } catch (RuntimeException ex) {
            payout.setStatus(CreatorPayoutStatus.FAILED);
            payout.setFailureReason(
                    "Unexpected Stripe transfer error: " + ex.getMessage()
            );
            payout.setFailedAt(Instant.now());
            payoutRequestRepository.save(payout);
            throw new CustomException(
                    502,
                    "Unexpected Stripe transfer error",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private MonthlyCalculation calculateMonthlyAmount(
            UUID userId,
            CreatorPayoutRole role,
            YearMonth month,
            CreatorPayoutSettingEntity settings
    ) {
        if (role == CreatorPayoutRole.TRANSLATOR) {
            BigDecimal defaultPageRate = normalizeUsd(settings.getTranslatorTaskRateUsd());
            List<TranslatorTaskRevenueResponse> rows = loadTranslatorRevenue(userId, month);
            BigDecimal monthlyGross = normalizeUsd(rows.stream()
                    .map(TranslatorTaskRevenueResponse::getRevenueUsd)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            BigDecimal cumulativeEarned = normalizeUsd(
                    translatorEarningRepository.sumAmountThroughMonth(userId, month.toString())
            );
            BigDecimal reserved = normalizeUsd(payoutRequestRepository.sumReservedThroughMonth(
                    userId,
                    month.toString(),
                    List.of(
                            CreatorPayoutStatus.PENDING,
                            CreatorPayoutStatus.APPROVED,
                            CreatorPayoutStatus.PROCESSING,
                            CreatorPayoutStatus.PAID
                    )
            ));
            BigDecimal availableBalance = normalizeUsd(
                    cumulativeEarned.subtract(reserved).max(BigDecimal.ZERO)
            );

            YearMonth currentMonth = YearMonth.now(payoutZone());
            BigDecimal pendingCurrentMonth = normalizeUsd(
                    translatorEarningRepository
                            .findAllByTranslatorIdAndEntryMonthOrderByCreatedAtAsc(userId, currentMonth.toString())
                            .stream()
                            .map(TranslatorEarningEntryEntity::getAmountUsd)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
            );

            long totalPages = rows.stream()
                    .filter(item -> "SETTLEMENT".equals(item.getRowType()))
                    .map(TranslatorTaskRevenueResponse::getCompletedPageCount)
                    .filter(Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();

            return new MonthlyCalculation(
                    monthlyGross,
                    availableBalance,
                    BigDecimal.ZERO.setScale(2),
                    cumulativeEarned,
                    availableBalance,
                    pendingCurrentMonth,
                    BigDecimal.valueOf(totalPages),
                    "approved pages in fully completed chapters",
                    defaultPageRate,
                    totalPages + " approved page(s); chapter reward is divided by total chapter pages and multiplied by coefficient K",
                    "Page work is credited only after the whole chapter is approved. Closed-month earnings accumulate as wallet balance; unused balance carries forward and monthly caps are not applied.",
                    rows,
                    List.of()
            );
        }

        long viewUnitSize = Math.max(1L, settings.getAuthorViewsPerUnit());
        long followUnitSize = Math.max(1L, settings.getAuthorFollowsPerUnit());
        BigDecimal viewRate = normalizeUsd(settings.getAuthorViewUnitRateUsd());
        BigDecimal followRate = normalizeUsd(settings.getAuthorFollowUnitRateUsd());
        List<AuthorComicRevenueResponse> comics = loadAuthorComicRevenue(
                userId,
                month,
                viewUnitSize,
                viewRate,
                followUnitSize,
                followRate
        );
        BigDecimal gross = normalizeUsd(comics.stream()
                .map(AuthorComicRevenueResponse::getTotalRevenueUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal limit = normalizeUsd(settings.getAuthorMonthlyLimitUsd());
        BigDecimal cappedGross = normalizeUsd(gross.min(limit));

        BigDecimal reservedThisMonth = sumBaseUsd(
                payoutRequestRepository
                        .findAllByUserIdAndPayoutMonthAndDeletedFalseOrderByCreatedAtDesc(
                                userId,
                                month.toString()
                        )
                        .stream()
                        .filter(item -> item.getRole() == CreatorPayoutRole.AUTHOR)
                        .filter(item -> item.getStatus() == CreatorPayoutStatus.PENDING
                                || item.getStatus() == CreatorPayoutStatus.APPROVED
                                || item.getStatus() == CreatorPayoutStatus.PROCESSING
                                || item.getStatus() == CreatorPayoutStatus.PAID)
                        .toList()
        );
        BigDecimal withdrawable = normalizeUsd(
                cappedGross.subtract(reservedThisMonth).max(BigDecimal.ZERO)
        );
        BigDecimal totalUnits = comics.stream()
                .map(item -> item.getViewUnits().add(item.getFollowUnits()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);

        return new MonthlyCalculation(
                gross,
                withdrawable,
                limit,
                gross,
                withdrawable,
                BigDecimal.ZERO.setScale(2),
                totalUnits,
                "proportional view/follow reward units",
                null,
                "Per comic: (views/" + viewUnitSize + ") x $" + viewRate.toPlainString()
                        + " + (follows/" + followUnitSize + ") x $" + followRate.toPlainString()
                        + "; partial units are paid; reserved/paid this month=$"
                        + reservedThisMonth.toPlainString(),
                "Rewards are proportional to every qualified view/follow, calculated per comic in USD, summed, capped, then reduced by pending/approved/processing/paid requests for the same month so the author cannot be paid twice.",
                List.of(),
                comics
        );
    }

    private List<TranslatorTaskRevenueResponse> loadTranslatorRevenue(UUID translatorId, YearMonth month) {
        List<TranslatorEarningEntryEntity> allEntries = translatorEarningRepository
                .findAllByTranslatorIdAndEntryMonthOrderByCreatedAtAsc(
                        translatorId,
                        month.toString()
                );

        List<TranslatorEarningEntryEntity> pageEarnings = allEntries.stream()
                .filter(item -> item.getEntryType() == TranslatorEarningEntryType.PAGE_EARNING)
                .toList();
        Map<UUID, List<TranslatorEarningEntryEntity>> bySettlement = pageEarnings.stream()
                .collect(Collectors.groupingBy(
                        TranslatorEarningEntryEntity::getSettlementId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<TranslatorTaskRevenueResponse> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<TranslatorEarningEntryEntity>> entry : bySettlement.entrySet()) {
            TranslatorChapterSettlementEntity settlement = translatorSettlementRepository
                    .findById(entry.getKey()).orElse(null);
            List<TranslatorEarningEntryEntity> pageRows = entry.getValue();
            if (pageRows.isEmpty()) continue;
            TeamTaskEntity task = teamTaskRepository.findByIdWithChapter(pageRows.get(0).getTaskId()).orElse(null);
            BigDecimal gross = normalizeUsd(pageRows.stream()
                    .map(TranslatorEarningEntryEntity::getGrossAmountUsd)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal net = normalizeUsd(pageRows.stream()
                    .map(TranslatorEarningEntryEntity::getAmountUsd)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal averageFactor = gross.signum() == 0
                    ? BigDecimal.ONE.setScale(2)
                    : net.divide(gross, 2, RoundingMode.HALF_UP);

            rows.add(TranslatorTaskRevenueResponse.builder()
                    .settlementId(entry.getKey())
                    .settlementVersion(settlement == null ? null : settlement.getVersionNo())
                    .taskId(pageRows.get(0).getTaskId())
                    .chapterId(pageRows.get(0).getChapterId())
                    .taskTitle(task == null ? "Translation task" : task.getTitle())
                    .chapterNumber(task == null || task.getChapter() == null ? null : task.getChapter().getChapterNumber())
                    .chapterTitle(task == null || task.getChapter() == null ? null : task.getChapter().getTitle())
                    .completedAt(task == null ? null : task.getCompletedAt())
                    .settledAt(settlement == null ? null : settlement.getSettledAt())
                    .completedPageCount(pageRows.size())
                    .totalPageCount(settlement == null ? null : settlement.getTotalPages())
                    .pageRateUsd(settlement == null ? null : settlement.getPageRateUsd())
                    .grossBeforeFactorUsd(gross)
                    .averageResponsibilityFactor(averageFactor)
                    .adjustmentUsd(BigDecimal.ZERO.setScale(2))
                    .revenueUsd(net)
                    .rowType("SETTLEMENT")
                    .note(settlement != null && settlement.getStatus() != null
                            ? "Settlement " + settlement.getStatus().name().toLowerCase()
                            : "Chapter settlement")
                    .build());
        }

        for (TranslatorEarningEntryEntity adjustment : allEntries.stream()
                .filter(item -> item.getEntryType() != TranslatorEarningEntryType.PAGE_EARNING)
                .toList()) {
            TeamTaskEntity task = teamTaskRepository.findByIdWithChapter(adjustment.getTaskId()).orElse(null);
            rows.add(TranslatorTaskRevenueResponse.builder()
                    .settlementId(adjustment.getSettlementId())
                    .taskId(adjustment.getTaskId())
                    .chapterId(adjustment.getChapterId() != null
                            ? adjustment.getChapterId()
                            : task == null || task.getChapter() == null ? null : task.getChapter().getId())
                    .taskTitle(task == null ? "Translation adjustment" : task.getTitle())
                    .chapterNumber(task == null || task.getChapter() == null ? null : task.getChapter().getChapterNumber())
                    .chapterTitle(task == null || task.getChapter() == null ? null : task.getChapter().getTitle())
                    .settledAt(adjustment.getCreatedAt())
                    .completedPageCount(0)
                    .grossBeforeFactorUsd(BigDecimal.ZERO.setScale(2))
                    .adjustmentUsd(normalizeUsd(adjustment.getAmountUsd()))
                    .revenueUsd(normalizeUsd(adjustment.getAmountUsd()))
                    .rowType("ADJUSTMENT")
                    .note(adjustment.getReason())
                    .build());
        }
        rows.sort((left, right) -> {
            Instant a = left.getSettledAt();
            Instant b = right.getSettledAt();
            if (a == null && b == null) return 0;
            if (a == null) return -1;
            if (b == null) return 1;
            return a.compareTo(b);
        });
        return rows;
    }

    private List<AuthorComicRevenueResponse> loadAuthorComicRevenue(
            UUID authorId,
            YearMonth month,
            long viewUnitSize,
            BigDecimal viewRate,
            long followUnitSize,
            BigDecimal followRate
    ) {
        LocalDate fromDate = month.atDay(1);
        LocalDate toDate = month.plusMonths(1).atDay(1);
        OffsetDateTime fromTimestamp = fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toTimestamp = toDate.atStartOfDay().atOffset(ZoneOffset.UTC);

        String sql = """
                SELECT c.id AS comic_id, c.title AS comic_title,
                       COALESCE(v.monthly_views, 0) AS monthly_views,
                       COALESCE(f.monthly_follows, 0) AS monthly_follows
                FROM comics c
                LEFT JOIN (
                    SELECT comic_id, SUM(view_count)::bigint AS monthly_views
                    FROM comic_daily_views
                    WHERE COALESCE(deleted, false) = false
                      AND log_date >= :fromDate
                      AND log_date < :toDate
                    GROUP BY comic_id
                ) v ON v.comic_id = c.id
                LEFT JOIN (
                    SELECT comic_id, COUNT(*)::bigint AS monthly_follows
                    FROM user_saves
                    WHERE COALESCE(deleted, false) = false
                      AND create_at >= :fromInstant
                      AND create_at < :toInstant
                    GROUP BY comic_id
                ) f ON f.comic_id = c.id
                WHERE COALESCE(c.deleted, false) = false
                  AND (
                      c.author_id = CAST(:authorId AS uuid)
                      OR c.author_id IN (
                          SELECT a.id FROM authors a
                          WHERE a.user_id = CAST(:authorId AS uuid)
                            AND COALESCE(a.deleted, false) = false
                      )
                  )
                ORDER BY c.title
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("authorId", authorId.toString())
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("fromInstant", fromTimestamp, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("toInstant", toTimestamp, Types.TIMESTAMP_WITH_TIMEZONE);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> mapAuthorRevenue(
                        rs,
                        viewUnitSize,
                        viewRate,
                        followUnitSize,
                        followRate
                )
        );
    }

    private AuthorComicRevenueResponse mapAuthorRevenue(
            ResultSet rs,
            long viewUnitSize,
            BigDecimal viewRate,
            long followUnitSize,
            BigDecimal followRate
    ) throws SQLException {
        long views = rs.getLong("monthly_views");
        long follows = rs.getLong("monthly_follows");
        BigDecimal viewUnits = proportionalUnits(views, viewUnitSize);
        BigDecimal followUnits = proportionalUnits(follows, followUnitSize);
        BigDecimal viewRevenue = proportionalReward(views, viewUnitSize, viewRate);
        BigDecimal followRevenue = proportionalReward(follows, followUnitSize, followRate);

        return AuthorComicRevenueResponse.builder()
                .comicId(rs.getObject("comic_id", UUID.class))
                .comicTitle(rs.getString("comic_title"))
                .monthlyViews(views)
                .viewUnits(viewUnits)
                .viewRevenueUsd(viewRevenue)
                .monthlyFollows(follows)
                .followUnits(followUnits)
                .followRevenueUsd(followRevenue)
                .totalRevenueUsd(normalizeUsd(viewRevenue.add(followRevenue)))
                .build();
    }

    private BigDecimal proportionalUnits(long count, long unitSize) {
        long safeUnitSize = Math.max(1L, unitSize);
        return BigDecimal.valueOf(Math.max(0L, count))
                .divide(BigDecimal.valueOf(safeUnitSize), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal proportionalReward(long count, long unitSize, BigDecimal unitRateUsd) {
        long safeUnitSize = Math.max(1L, unitSize);
        return normalizeUsd(
                BigDecimal.valueOf(Math.max(0L, count))
                        .multiply(normalizeUsd(unitRateUsd))
                        .divide(BigDecimal.valueOf(safeUnitSize), 8, RoundingMode.HALF_UP)
        );
    }

    private Requestability evaluateRequestability(
            CreatorPayoutAccountEntity account,
            CreatorPayoutRequestEntity existing,
            YearMonth selectedMonth,
            BigDecimal withdrawableUsd,
            BigDecimal minimumUsd
    ) {
        if (selectedMonth.isAfter(latestRequestableMonth())) {
            return new Requestability(
                    false,
                    allowCurrentMonth
                            ? "Future months cannot be requested"
                            : "Only completed months can be requested"
            );
        }
        if (account == null) {
            return new Requestability(false, "Set up your Stripe payout method first");
        }
        if (!payoutProfileService.isReady(account)) {
            return new Requestability(false, "Complete Stripe onboarding and add a payout method first");
        }
        if (existing != null && !isRetryable(existing.getStatus())) {
            return new Requestability(false, "Another payout request is still pending for this month");
        }
        if (withdrawableUsd.compareTo(minimumUsd) < 0) {
            return new Requestability(false, "Available accumulated balance is below the minimum payout amount");
        }
        return new Requestability(true, null);
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
        if ("AUTHOR".equalsIgnoreCase(roleName)) return CreatorPayoutRole.AUTHOR;
        if ("TRANSLATOR".equalsIgnoreCase(roleName)) return CreatorPayoutRole.TRANSLATOR;
        throw new CustomException(
                403,
                "Only Author and Translator accounts can request creator payouts",
                HttpStatus.FORBIDDEN
        );
    }

    private YearMonth resolveMonth(String value) {
        return StringUtils.hasText(value) ? parseMonth(value) : latestRequestableMonth();
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException ex) {
            throw new CustomException(400, "Payout month must use YYYY-MM format", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureRequestableMonth(YearMonth month) {
        if (month.isAfter(latestRequestableMonth())) {
            String message = allowCurrentMonth
                    ? "Payout requests cannot be created for a future month"
                    : "Payout requests are available only after the month is closed";
            throw new CustomException(400, message, HttpStatus.BAD_REQUEST);
        }
    }

    private YearMonth latestRequestableMonth() {
        return allowCurrentMonth ? YearMonth.now(payoutZone()) : lastClosedMonth();
    }

    private YearMonth lastClosedMonth() {
        return YearMonth.now(payoutZone()).minusMonths(1);
    }

    private boolean isActiveReservation(CreatorPayoutStatus status) {
        return status == CreatorPayoutStatus.PENDING
                || status == CreatorPayoutStatus.APPROVED
                || status == CreatorPayoutStatus.PROCESSING;
    }

    private boolean isRetryable(CreatorPayoutStatus status) {
        return status == CreatorPayoutStatus.REJECTED || status == CreatorPayoutStatus.FAILED;
    }

    private BigDecimal sumBaseUsdByStatuses(
            List<CreatorPayoutRequestEntity> entities,
            List<CreatorPayoutStatus> statuses
    ) {
        return sumBaseUsd(entities.stream()
                .filter(item -> statuses.contains(item.getStatus()))
                .toList());
    }

    private BigDecimal sumBaseUsd(List<CreatorPayoutRequestEntity> entities) {
        return normalizeUsd(entities.stream()
                .map(item -> item.getBaseAmountUsd() != null ? item.getBaseAmountUsd() : item.getAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal convert(
            BigDecimal usdAmount,
            CreatorPayoutSettingsService.ResolvedCurrency currency
    ) {
        return payoutSettingsService.convertUsdToCurrency(
                usdAmount,
                currency
        );
    }

    private BigDecimal normalizeUsd(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private ZoneId payoutZone() {
        try {
            return ZoneId.of(payoutTimeZone);
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }

    private String normalizeCountryOrDefault(String countryCode) {
        if (!StringUtils.hasText(countryCode) || !countryCode.trim().matches("^[A-Za-z]{2}$")) {
            return "VN";
        }
        return countryCode.trim().toUpperCase();
    }

    private String displayName(UserEntity user) {
        return StringUtils.hasText(user.getFullName()) ? user.getFullName().trim() : user.getUsername();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private CreatorPayoutRequestResponse toRequestResponse(
            CreatorPayoutRequestEntity payout
    ) {
        if (payout == null) return null;

        CreatorPayoutCurrency currency = CreatorPayoutCurrency.USD;
        BigDecimal amountUsd = normalizeUsd(
                payout.getBaseAmountUsd() != null
                        ? payout.getBaseAmountUsd()
                        : payout.getAmount()
        );
        BigDecimal unitsPerUsd = BigDecimal.ONE.setScale(6);
        BigDecimal amount = amountUsd;

        return CreatorPayoutRequestResponse.builder()
                .id(payout.getId())
                .userId(payout.getUserId())
                .userName(payout.getUserName())
                .userEmail(payout.getUserEmail())
                .role(payout.getRole())
                .payoutMonth(payout.getPayoutMonth())
                .amount(amount)
                .amountUsd(amountUsd)
                .grossAmountUsd(normalizeUsd(payout.getGrossAmountUsd()))
                .monthlyLimitUsd(normalizeUsd(payout.getMonthlyLimitUsd()))
                .unitsPerUsd(unitsPerUsd)
                .accountCountry(payout.getAccountCountry())
                .currency(currency.getCode())
                .currencySymbol(currency.getSymbol())
                .status(payout.getStatus())
                .stripeConnectedAccountId(
                        payout.getStripeConnectedAccountId()
                )
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
            BigDecimal grossUsd,
            BigDecimal withdrawableUsd,
            BigDecimal monthlyLimitUsd,
            BigDecimal cumulativeEarnedUsd,
            BigDecimal availableBalanceUsd,
            BigDecimal pendingCurrentMonthUsd,
            BigDecimal unitCount,
            String unitLabel,
            BigDecimal unitRateUsd,
            String details,
            String policy,
            List<TranslatorTaskRevenueResponse> translatorTasks,
            List<AuthorComicRevenueResponse> authorComics
    ) {}

    private record Requestability(boolean requestable, String reason) {}
}
