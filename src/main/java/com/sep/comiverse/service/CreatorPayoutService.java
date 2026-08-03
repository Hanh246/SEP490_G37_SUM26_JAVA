package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep.comiverse.dto.request.CreatePayoutRequest;
import com.sep.comiverse.dto.response.AdminPayoutPageResponse;
import com.sep.comiverse.dto.response.AuthorComicRevenueResponse;
import com.sep.comiverse.dto.response.CreatorPayoutOverviewResponse;
import com.sep.comiverse.dto.response.CreatorPayoutRequestResponse;
import com.sep.comiverse.dto.response.TranslatorTaskRevenueResponse;
import com.sep.comiverse.entity.CreatorStripePayoutProfileEntity;
import com.sep.comiverse.entity.CreatorPayoutRequestEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.TeamTaskEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.IAuthorRepository;
import com.sep.comiverse.repository.ICreatorPayoutRequestRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatorPayoutService {

    private static final int MAX_ADMIN_PAGE_SIZE = 100;

    @Value("${payout.allow-current-month:false}")
    private boolean allowCurrentMonth;

    private final CreatorStripePayoutProfileService payoutProfileService;
    private final IAuthorRepository authorRepository;
    private final ICreatorPayoutRequestRepository payoutRequestRepository;
    private final ITeamTaskRepository teamTaskRepository;
    private final IUserRepository userRepository;
    private final StripeGatewayService stripeGatewayService;
    private final CreatorPayoutSettingsService payoutSettingsService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public CreatorPayoutOverviewResponse getOverview(UserPrincipal principal, String requestedMonth) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);
        YearMonth selectedMonth = resolveMonth(requestedMonth);
        CreatorPayoutSettingEntity settings = payoutSettingsService.currentSettings();

        CreatorStripePayoutProfileEntity account = payoutProfileService.findEntity(user.getId());
        CreatorPayoutSettingsService.ResolvedCurrency currency = resolveCurrencyForRole(
                user.getId(), role, account == null ? null : account.getAccountCountry());
        MonthlyCalculation calculation = calculateMonthlyAmount(user.getId(), role, selectedMonth, settings);

        List<CreatorPayoutRequestEntity> entities = payoutRequestRepository
                .findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(user.getId());
        CreatorPayoutRequestEntity existing = entities.stream()
                .filter(item -> selectedMonth.toString().equals(item.getPayoutMonth()))
                .findFirst()
                .orElse(null);

        BigDecimal lifetimePaidVnd = sumBaseVndByStatuses(entities, List.of(CreatorPayoutStatus.PAID));
        BigDecimal pendingVnd = sumBaseVndByStatuses(
                entities,
                List.of(CreatorPayoutStatus.PENDING, CreatorPayoutStatus.APPROVED, CreatorPayoutStatus.PROCESSING)
        );
        BigDecimal minimumVnd = normalizeVnd(settings.getMinimumPayoutVnd());
        Requestability requestability = evaluateRequestability(
                account, existing, selectedMonth, calculation.withdrawableVnd(), minimumVnd
        );
        BigDecimal overLimitVnd = normalizeVnd(
                calculation.grossVnd().subtract(calculation.withdrawableVnd()).max(BigDecimal.ZERO)
        );

        return CreatorPayoutOverviewResponse.builder()
                .role(role)
                .selectedMonth(selectedMonth.toString())
                .lastClosedMonth(lastClosedMonth().toString())
                .latestRequestableMonth(latestRequestableMonth().toString())
                .currentMonthAllowed(allowCurrentMonth)
                .monthlyGrossAmount(payoutSettingsService.convertFromVnd(calculation.grossVnd(), currency))
                .monthlyGrossAmountVnd(calculation.grossVnd())
                .monthlyWithdrawableAmount(payoutSettingsService.convertFromVnd(calculation.withdrawableVnd(), currency))
                .monthlyWithdrawableAmountVnd(calculation.withdrawableVnd())
                .monthlyOverLimitAmount(payoutSettingsService.convertFromVnd(overLimitVnd, currency))
                .monthlyOverLimitAmountVnd(overLimitVnd)
                .monthlyLimitAmount(payoutSettingsService.convertFromVnd(calculation.monthlyLimitVnd(), currency))
                .monthlyLimitAmountVnd(calculation.monthlyLimitVnd())
                .minimumPayoutAmount(payoutSettingsService.convertFromVnd(minimumVnd, currency))
                .minimumPayoutAmountVnd(minimumVnd)
                .payoutCurrency(currency.currencyCode())
                .accountCountry(currency.countryCode())
                .exchangeRateVndPerUnit(currency.vndPerUnit())
                .lifetimePaidAmount(payoutSettingsService.convertFromVnd(lifetimePaidVnd, currency))
                .pendingAmount(payoutSettingsService.convertFromVnd(pendingVnd, currency))
                .requestable(requestability.requestable())
                .notRequestableReason(requestability.reason())
                .calculationUnitCount(calculation.unitCount())
                .calculationUnitLabel(calculation.unitLabel())
                .calculationUnitRate(payoutSettingsService.convertFromVnd(calculation.unitRateVnd(), currency))
                .translatorTaskRateVnd(settings.getTranslatorTaskRateVnd())
                .authorViewsPerUnit(settings.getAuthorViewsPerUnit())
                .authorViewUnitRateVnd(settings.getAuthorViewUnitRateVnd())
                .authorFollowsPerUnit(settings.getAuthorFollowsPerUnit())
                .authorFollowUnitRateVnd(settings.getAuthorFollowUnitRateVnd())
                .calculationPolicy(calculation.policy())
                .translatorTasks(calculation.translatorTasks())
                .authorComics(calculation.authorComics())
                .account(payoutProfileService.toResponse(account))
                .existingRequest(toRequestResponse(existing))
                .requests(entities.stream().map(this::toRequestResponse).toList())
                .build();
    }

    @Transactional
    public CreatorPayoutRequestResponse createRequest(UserPrincipal principal, CreatePayoutRequest request) {
        UserEntity user = requireCreator(principal);
        CreatorPayoutRole role = resolveCreatorRole(user);
        YearMonth payoutMonth = parseMonth(request.getPayoutMonth());
        ensureRequestableMonth(payoutMonth);

        CreatorStripePayoutProfileEntity account = payoutProfileService.requireReadyProfile(user.getId());
        CreatorPayoutSettingsService.ResolvedCurrency currency = resolveCurrencyForRole(
                user.getId(), role, account.getAccountCountry());

        CreatorPayoutSettingEntity settings = payoutSettingsService.currentSettings();
        MonthlyCalculation calculation = calculateMonthlyAmount(user.getId(), role, payoutMonth, settings);
        BigDecimal minimumVnd = normalizeVnd(settings.getMinimumPayoutVnd());

        BigDecimal requestedAmount = request.getRequestedAmount();
        if (requestedAmount == null || requestedAmount.signum() <= 0) {
            throw new CustomException(400, "Requested amount must be greater than zero", HttpStatus.BAD_REQUEST);
        }

        BigDecimal requestedVnd = role == CreatorPayoutRole.TRANSLATOR
                ? normalizeVnd(requestedAmount)
                : normalizeVnd(requestedAmount.multiply(currency.vndPerUnit()));
        if (requestedVnd.compareTo(minimumVnd) < 0) {
            throw new CustomException(400,
                    "Requested amount is below the minimum payout amount of "
                            + minimumVnd.toPlainString() + " VND",
                    HttpStatus.BAD_REQUEST);
        }
        if (requestedVnd.compareTo(calculation.withdrawableVnd()) > 0) {
            throw new CustomException(400,
                    "Requested amount exceeds the withdrawable amount for " + payoutMonth,
                    HttpStatus.BAD_REQUEST);
        }

        CreatorPayoutRequestEntity payout = payoutRequestRepository
                .findByUserIdAndPayoutMonthAndDeletedFalse(user.getId(), payoutMonth.toString())
                .orElseGet(CreatorPayoutRequestEntity::new);
        if (payout.getId() != null && !isRetryable(payout.getStatus())) {
            throw new CustomException(409, "A payout request already exists for " + payoutMonth, HttpStatus.CONFLICT);
        }

        BigDecimal convertedAmount = role == CreatorPayoutRole.TRANSLATOR
                ? requestedVnd
                : payoutSettingsService.convertFromVnd(requestedVnd, currency);
        payout.setUserId(user.getId());
        payout.setUserName(displayName(user));
        payout.setUserEmail(user.getEmail());
        payout.setRole(role);
        payout.setPayoutMonth(payoutMonth.toString());
        payout.setAmount(convertedAmount);
        payout.setGrossAmountVnd(calculation.grossVnd());
        payout.setBaseAmountVnd(requestedVnd);
        payout.setMonthlyLimitVnd(calculation.monthlyLimitVnd());
        payout.setExchangeRateVndPerUnit(currency.vndPerUnit());
        payout.setAccountCountry(currency.countryCode());
        payout.setCurrency(role == CreatorPayoutRole.TRANSLATOR ? "VND" : currency.currencyCode());
        payout.setStatus(CreatorPayoutStatus.PENDING);
        payout.setStripeConnectedAccountId(account.getStripeConnectedAccountId());
        payout.setStripeTransferId(null);
        payout.setRequestNote(trimToNull(request.getNote()));
        payout.setAdminNote(null);
        payout.setFailureReason(null);
        payout.setCalculationDetails(
                calculation.details()
                        + "; requested=" + requestedVnd.toPlainString() + " VND"
                        + "; available=" + calculation.withdrawableVnd().toPlainString() + " VND"
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
                    .filter(item -> item.getStatus() == itemStatus).toList();
            counts.put(itemStatus, (long) matching.size());
            totals.put(itemStatus, sumBaseVnd(matching));
        }
        return AdminPayoutPageResponse.builder()
                .items(result.getContent().stream().map(this::toRequestResponse).toList())
                .counts(counts).totals(totals)
                .page(result.getNumber()).size(result.getSize())
                .totalElements(result.getTotalElements()).totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public CreatorPayoutRequestResponse approve(UUID payoutId, String adminNote) {
        CreatorPayoutRequestEntity payout = getLockedPayout(payoutId);
        if (payout.getStatus() != CreatorPayoutStatus.PENDING) throw invalidTransition(payout, "approve");
        CreatorStripePayoutProfileEntity account = payoutProfileService.requireReadyProfile(payout.getUserId());
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
        if (payout.getStatus() != CreatorPayoutStatus.PENDING && payout.getStatus() != CreatorPayoutStatus.APPROVED) {
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
        if (payout.getStatus() != CreatorPayoutStatus.APPROVED && payout.getStatus() != CreatorPayoutStatus.FAILED) {
            throw invalidTransition(payout, "pay");
        }
        CreatorStripePayoutProfileEntity account = payoutProfileService.requireReadyProfile(payout.getUserId());
        payout.setStripeConnectedAccountId(account.getStripeConnectedAccountId());
        normalizeTranslatorPayoutSnapshot(payout);
        payout.setStatus(CreatorPayoutStatus.PROCESSING);
        payout.setFailureReason(null);
        payout.setFailedAt(null);
        payoutRequestRepository.saveAndFlush(payout);
        try {
            JsonNode transfer = stripeGatewayService.createTransfer(
                    payout.getStripeConnectedAccountId(), payout.getAmount(), payout.getCurrency(),
                    payout.getId(), payout.getUserId(), payout.getPayoutMonth());
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
        } catch (RuntimeException ex) {
            payout.setStatus(CreatorPayoutStatus.FAILED);
            payout.setFailureReason("Unexpected Stripe transfer error: " + ex.getMessage());
            payout.setFailedAt(Instant.now());
            payoutRequestRepository.save(payout);
            throw new CustomException(502, "Unexpected Stripe transfer error", HttpStatus.BAD_GATEWAY);
        }
    }

    private MonthlyCalculation calculateMonthlyAmount(
            UUID userId, CreatorPayoutRole role, YearMonth month, CreatorPayoutSettingEntity settings) {
        Instant from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (role == CreatorPayoutRole.TRANSLATOR) {
            BigDecimal rate = normalizeVnd(settings.getTranslatorTaskRateVnd());
            List<TranslatorTaskRevenueResponse> rows = teamTaskRepository
                    .findCompletedForAssigneeInPeriod(userId, from, to).stream()
                    .map(task -> toTranslatorRevenue(task, rate)).toList();
            BigDecimal gross = normalizeVnd(rate.multiply(BigDecimal.valueOf(rows.size())));
            BigDecimal limit = normalizeVnd(settings.getTranslatorMonthlyLimitVnd());
            BigDecimal withdrawable = gross.min(limit);
            return new MonthlyCalculation(gross, withdrawable, limit, (long) rows.size(),
                    "completed assigned tasks", rate,
                    rows.size() + " completed team_tasks by assignee_id x " + rate.toPlainString() + " VND",
                    "Only team_tasks with assignee_id equal to the Translator, completed status, and completed_at in the selected month are paid.",
                    rows, List.of());
        }

        long viewUnitSize = Math.max(1L, settings.getAuthorViewsPerUnit());
        long followUnitSize = Math.max(1L, settings.getAuthorFollowsPerUnit());
        BigDecimal viewRate = normalizeVnd(settings.getAuthorViewUnitRateVnd());
        BigDecimal followRate = normalizeVnd(settings.getAuthorFollowUnitRateVnd());
        List<AuthorComicRevenueResponse> comics = loadAuthorComicRevenue(userId, month, viewUnitSize, viewRate, followUnitSize, followRate);
        BigDecimal gross = normalizeVnd(comics.stream().map(AuthorComicRevenueResponse::getTotalRevenueVnd)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal limit = normalizeVnd(settings.getAuthorMonthlyLimitVnd());
        BigDecimal withdrawable = gross.min(limit);
        long totalUnits = comics.stream().mapToLong(item -> item.getViewUnits() + item.getFollowUnits()).sum();
        return new MonthlyCalculation(gross, withdrawable, limit, totalUnits,
                "view/follow reward units", null,
                "Per comic: floor(views/" + viewUnitSize + ") x " + viewRate.toPlainString()
                        + " VND + floor(follows/" + followUnitSize + ") x " + followRate.toPlainString() + " VND",
                "Rewards are calculated separately for each comic, then summed and capped at the Author monthly withdrawal limit.",
                List.of(), comics);
    }

    private TranslatorTaskRevenueResponse toTranslatorRevenue(TeamTaskEntity task, BigDecimal rate) {
        return TranslatorTaskRevenueResponse.builder()
                .taskId(task.getId())
                .chapterId(task.getChapter() == null ? null : task.getChapter().getId())
                .taskTitle(task.getTitle())
                .chapterNumber(task.getChapter() == null ? null : task.getChapter().getChapterNumber())
                .chapterTitle(task.getChapter() == null ? null : task.getChapter().getTitle())
                .completedAt(task.getCompletedAt())
                .revenueVnd(rate)
                .build();
    }

    private List<AuthorComicRevenueResponse> loadAuthorComicRevenue(
            UUID authorId, YearMonth month, long viewUnitSize, BigDecimal viewRate,
            long followUnitSize, BigDecimal followRate) {
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
                    WHERE COALESCE(deleted, false) = false AND log_date >= :fromDate AND log_date < :toDate
                    GROUP BY comic_id
                ) v ON v.comic_id = c.id
                LEFT JOIN (
                    SELECT comic_id, COUNT(*)::bigint AS monthly_follows
                    FROM user_saves
                    WHERE COALESCE(deleted, false) = false AND create_at >= :fromInstant AND create_at < :toInstant
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
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapAuthorRevenue(
                rs, viewUnitSize, viewRate, followUnitSize, followRate));
    }

    private AuthorComicRevenueResponse mapAuthorRevenue(
            ResultSet rs, long viewUnitSize, BigDecimal viewRate,
            long followUnitSize, BigDecimal followRate) throws SQLException {
        long views = rs.getLong("monthly_views");
        long follows = rs.getLong("monthly_follows");
        long viewUnits = views / viewUnitSize;
        long followUnits = follows / followUnitSize;
        BigDecimal viewRevenue = normalizeVnd(viewRate.multiply(BigDecimal.valueOf(viewUnits)));
        BigDecimal followRevenue = normalizeVnd(followRate.multiply(BigDecimal.valueOf(followUnits)));
        return AuthorComicRevenueResponse.builder()
                .comicId(rs.getObject("comic_id", UUID.class))
                .comicTitle(rs.getString("comic_title"))
                .monthlyViews(views).viewUnits(viewUnits).viewRevenueVnd(viewRevenue)
                .monthlyFollows(follows).followUnits(followUnits).followRevenueVnd(followRevenue)
                .totalRevenueVnd(normalizeVnd(viewRevenue.add(followRevenue)))
                .build();
    }


    private CreatorPayoutSettingsService.ResolvedCurrency resolveCurrencyForRole(
            UUID userId, CreatorPayoutRole role, String fallbackCountry) {
        String normalizedFallbackCountry = normalizeCountryOrDefault(fallbackCountry);
        if (role == CreatorPayoutRole.TRANSLATOR) {
            // Translator revenue, withdrawal limits, payout requests, and Stripe transfers
            // are always stored and paid in VND. The Stripe account country is retained
            // only as account metadata; no country/currency conversion is applied.
            return new CreatorPayoutSettingsService.ResolvedCurrency(
                    normalizedFallbackCountry, "VND", BigDecimal.ONE);
        }

        String authorCountry = authorRepository.findByUserIdAndDeletedFalse(userId)
                .map(author -> author.getCountryCode())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .orElse(normalizedFallbackCountry);
        return payoutSettingsService.resolveCurrency(authorCountry);
    }

    private String normalizeCountryOrDefault(String countryCode) {
        if (!StringUtils.hasText(countryCode) || !countryCode.trim().matches("^[A-Za-z]{2}$")) {
            return "VN";
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private Requestability evaluateRequestability(CreatorStripePayoutProfileEntity account,
                                                  CreatorPayoutRequestEntity existing, YearMonth selectedMonth,
                                                  BigDecimal withdrawableVnd, BigDecimal minimumVnd) {
        if (selectedMonth.isAfter(latestRequestableMonth())) {
            return new Requestability(false, allowCurrentMonth
                    ? "Future months cannot be requested"
                    : "Only completed months can be requested");
        }
        if (account == null) {
            return new Requestability(false, "Set up your Stripe payout method first");
        }
        if (!payoutProfileService.isReady(account)) {
            return new Requestability(false, "Complete Stripe onboarding and add a payout method first");
        }
        if (existing != null && !isRetryable(existing.getStatus())) {
            return new Requestability(false, "A payout request already exists for this month");
        }
        if (withdrawableVnd.compareTo(minimumVnd) < 0) {
            return new Requestability(false, "Monthly earnings are below the minimum payout amount");
        }
        return new Requestability(true, null);
    }

    private CreatorPayoutRequestEntity getLockedPayout(UUID payoutId) {
        if (payoutId == null) throw new CustomException(400, "Payout request ID is required", HttpStatus.BAD_REQUEST);
        return payoutRequestRepository.findLockedById(payoutId)
                .orElseThrow(() -> new CustomException(404, "Payout request not found", HttpStatus.NOT_FOUND));
    }

    private CustomException invalidTransition(CreatorPayoutRequestEntity payout, String action) {
        return new CustomException(409, "Cannot " + action + " payout while status is " + payout.getStatus(), HttpStatus.CONFLICT);
    }

    private UserEntity requireCreator(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) throw new CustomException(401, "Authentication is required", HttpStatus.UNAUTHORIZED);
        UserEntity user = userRepository.findByIdWithRole(principal.getId())
                .orElseThrow(() -> new CustomException(404, "User not found", HttpStatus.NOT_FOUND));
        resolveCreatorRole(user);
        return user;
    }

    private CreatorPayoutRole resolveCreatorRole(UserEntity user) {
        String roleName = user == null || user.getRole() == null ? "" : user.getRole().getRoleName();
        if ("AUTHOR".equalsIgnoreCase(roleName)) return CreatorPayoutRole.AUTHOR;
        if ("TRANSLATOR".equalsIgnoreCase(roleName)) return CreatorPayoutRole.TRANSLATOR;
        throw new CustomException(403, "Only Author and Translator accounts can request creator payouts", HttpStatus.FORBIDDEN);
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
        return allowCurrentMonth
                ? YearMonth.now(ZoneOffset.UTC)
                : lastClosedMonth();
    }

    private YearMonth lastClosedMonth() {
        return YearMonth.now(ZoneOffset.UTC).minusMonths(1);
    }
    private boolean isRetryable(CreatorPayoutStatus status) { return status == CreatorPayoutStatus.REJECTED || status == CreatorPayoutStatus.FAILED; }

    private void normalizeTranslatorPayoutSnapshot(CreatorPayoutRequestEntity payout) {
        if (payout == null || payout.getRole() != CreatorPayoutRole.TRANSLATOR) return;
        BigDecimal vndAmount = payout.getBaseAmountVnd() != null
                ? payout.getBaseAmountVnd()
                : payout.getAmount();
        payout.setAmount(normalizeVnd(vndAmount));
        payout.setCurrency("VND");
        payout.setExchangeRateVndPerUnit(BigDecimal.ONE);
    }

    private BigDecimal sumBaseVndByStatuses(List<CreatorPayoutRequestEntity> entities, List<CreatorPayoutStatus> statuses) {
        return sumBaseVnd(entities.stream().filter(item -> statuses.contains(item.getStatus())).toList());
    }
    private BigDecimal sumBaseVnd(List<CreatorPayoutRequestEntity> entities) {
        return normalizeVnd(entities.stream().map(item -> {
            if (item.getBaseAmountVnd() != null) return item.getBaseAmountVnd();
            if ("VND".equalsIgnoreCase(item.getCurrency())) return item.getAmount();
            return BigDecimal.ZERO;
        }).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private BigDecimal normalizeVnd(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(0, RoundingMode.HALF_UP);
    }
    private String displayName(UserEntity user) { return StringUtils.hasText(user.getFullName()) ? user.getFullName().trim() : user.getUsername(); }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private CreatorPayoutRequestResponse toRequestResponse(CreatorPayoutRequestEntity payout) {
        if (payout == null) return null;
        boolean translator = payout.getRole() == CreatorPayoutRole.TRANSLATOR;
        BigDecimal responseAmount = translator
                ? normalizeVnd(payout.getBaseAmountVnd() != null ? payout.getBaseAmountVnd() : payout.getAmount())
                : payout.getAmount();
        return CreatorPayoutRequestResponse.builder()
                .id(payout.getId()).userId(payout.getUserId()).userName(payout.getUserName()).userEmail(payout.getUserEmail())
                .role(payout.getRole()).payoutMonth(payout.getPayoutMonth()).amount(responseAmount)
                .grossAmountVnd(payout.getGrossAmountVnd()).baseAmountVnd(payout.getBaseAmountVnd())
                .monthlyLimitVnd(payout.getMonthlyLimitVnd())
                .exchangeRateVndPerUnit(translator ? BigDecimal.ONE : payout.getExchangeRateVndPerUnit())
                .accountCountry(payout.getAccountCountry()).currency(translator ? "VND" : payout.getCurrency()).status(payout.getStatus())
                .stripeConnectedAccountId(payout.getStripeConnectedAccountId()).stripeTransferId(payout.getStripeTransferId())
                .requestNote(payout.getRequestNote()).adminNote(payout.getAdminNote()).failureReason(payout.getFailureReason())
                .calculationDetails(payout.getCalculationDetails()).requestedAt(payout.getRequestedAt())
                .approvedAt(payout.getApprovedAt()).paidAt(payout.getPaidAt()).rejectedAt(payout.getRejectedAt())
                .failedAt(payout.getFailedAt()).createdAt(payout.getCreatedAt()).updatedAt(payout.getUpdatedAt()).build();
    }

    private record MonthlyCalculation(BigDecimal grossVnd, BigDecimal withdrawableVnd, BigDecimal monthlyLimitVnd,
                                      Long unitCount, String unitLabel, BigDecimal unitRateVnd, String details, String policy,
                                      List<TranslatorTaskRevenueResponse> translatorTasks, List<AuthorComicRevenueResponse> authorComics) {}
    private record Requestability(boolean requestable, String reason) {}
}