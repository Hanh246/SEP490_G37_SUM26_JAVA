package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.CreatePayoutRequest;
import com.sep.comiverse.dto.response.AuthorComicRevenueResponse;
import com.sep.comiverse.entity.CreatorPayoutAccountEntity;
import com.sep.comiverse.entity.CreatorPayoutRequestEntity;
import com.sep.comiverse.entity.CreatorPayoutSettingEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutCurrency;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ICreatorPayoutRequestRepository;
import com.sep.comiverse.repository.ITeamTaskRepository;
import com.sep.comiverse.repository.ITranslatorChapterSettlementRepository;
import com.sep.comiverse.repository.ITranslatorEarningEntryRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.AuthorLicenseService;
import com.sep.comiverse.service.CreatorPayoutAccountService;
import com.sep.comiverse.service.CreatorPayoutService;
import com.sep.comiverse.service.CreatorPayoutSettingsService;
import com.sep.comiverse.service.StripeGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorPayoutServiceTest {

    @Mock private CreatorPayoutAccountService payoutProfileService;
    @Mock private ICreatorPayoutRequestRepository payoutRequestRepository;
    @Mock private ITeamTaskRepository teamTaskRepository;
    @Mock private ITranslatorChapterSettlementRepository translatorSettlementRepository;
    @Mock private ITranslatorEarningEntryRepository translatorEarningRepository;
    @Mock private IUserRepository userRepository;
    @Mock private StripeGatewayService stripeGatewayService;
    @Mock private CreatorPayoutSettingsService payoutSettingsService;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock private AuthorLicenseService authorLicenseService;

    private CreatorPayoutService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CreatorPayoutService(
                payoutProfileService,
                payoutRequestRepository,
                teamTaskRepository,
                translatorSettlementRepository,
                translatorEarningRepository,
                userRepository,
                stripeGatewayService,
                payoutSettingsService,
                jdbcTemplate,
                authorLicenseService
        );

        ReflectionTestUtils.setField(service, "allowCurrentMonth", false);
        ReflectionTestUtils.setField(service, "payoutTimeZone", "Asia/Ho_Chi_Minh");

        lenient().when(payoutRequestRepository.save(any(CreatorPayoutRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(payoutRequestRepository.saveAndFlush(any(CreatorPayoutRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ===== createRequest: authentication / role / month =====

    @Test
    void createRequestRejectsUnauthenticatedPrincipal() {
        CreatePayoutRequest request = request(lastClosedMonth(), "25.00");

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(null, request)
        );

        assertEquals(401, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void createRequestRejectsMissingUser() {
        UserEntity translator = user("TRANSLATOR");
        when(userRepository.findByIdWithRole(translator.getId())).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(new UserPrincipal(translator), request(lastClosedMonth(), "25.00"))
        );

        assertEquals(404, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void createRequestRejectsNonCreatorRole() {
        UserEntity reader = user("READER");
        when(userRepository.findByIdWithRole(reader.getId())).thenReturn(Optional.of(reader));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(new UserPrincipal(reader), request(lastClosedMonth(), "25.00"))
        );

        assertEquals(403, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void createRequestRejectsMalformedPayoutMonth() {
        UserEntity translator = user("TRANSLATOR");
        when(userRepository.findByIdWithRole(translator.getId())).thenReturn(Optional.of(translator));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request("bad-month", "25.00")
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void createRequestRejectsCurrentMonthWhenCurrentMonthIsDisabled() {
        UserEntity translator = user("TRANSLATOR");
        when(userRepository.findByIdWithRole(translator.getId())).thenReturn(Optional.of(translator));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request(currentMonth(), "25.00")
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void createRequestRejectsFutureMonthWhenCurrentMonthIsEnabled() {
        ReflectionTestUtils.setField(service, "allowCurrentMonth", true);

        UserEntity translator = user("TRANSLATOR");
        when(userRepository.findByIdWithRole(translator.getId())).thenReturn(Optional.of(translator));

        String nextMonth = YearMonth.parse(currentMonth()).plusMonths(1).toString();

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request(nextMonth, "25.00")
                )
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void createRequestAcceptsCurrentMonthWhenCurrentMonthIsEnabled() {
        ReflectionTestUtils.setField(service, "allowCurrentMonth", true);

        UserEntity translator = user("TRANSLATOR");
        String month = currentMonth();

        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        var response = service.createRequest(
                new UserPrincipal(translator),
                request(month, "25.00")
        );

        assertEquals(CreatorPayoutStatus.PENDING, response.getStatus());
        assertEquals(month, response.getPayoutMonth());
    }

    @Test
    void createRequestAuthorMustPassLicenseGateBeforePayoutCalculation() {
        UserEntity author = user("AUTHOR");
        when(userRepository.findByIdWithRole(author.getId())).thenReturn(Optional.of(author));
        doThrow(new CustomException(
                403,
                "license inactive",
                org.springframework.http.HttpStatus.FORBIDDEN
        )).when(authorLicenseService).assertAuthorPayoutAllowed(author.getId());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(author),
                        request(lastClosedMonth(), "25.00")
                )
        );

        assertEquals(403, error.getCode());
        verifyNoInteractions(payoutProfileService);
    }

    @Test
    void createRequestRejectsWhenStripePayoutProfileIsNotReady() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();

        when(userRepository.findByIdWithRole(translator.getId()))
                .thenReturn(Optional.of(translator));
        when(payoutProfileService.requireReadyProfile(translator.getId()))
                .thenThrow(new CustomException(
                        400,
                        "Stripe payout profile is not ready",
                        org.springframework.http.HttpStatus.BAD_REQUEST
                ));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request(month, "25.00")
                )
        );

        assertEquals(400, error.getCode());
        verify(payoutRequestRepository, never()).save(any());
        verifyNoInteractions(stripeGatewayService);
    }

    // ===== createRequest: currency / amount / reservation Decision Table =====

    @Test
    void createRequestRejectsCurrencyDifferentFromReadyStripeAccount() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        CreatePayoutRequest.builder()
                                .payoutMonth(month)
                                .requestedAmount(new BigDecimal("25.00"))
                                .payoutCurrency("EUR")
                                .build()
                )
        );

        assertEquals(400, error.getCode());
        verify(payoutRequestRepository, never()).save(argThat(p -> p.getStatus() == CreatorPayoutStatus.PENDING));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    void createRequestRejectsNonPositiveRequestedAmount(String amount) {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request(month, amount)
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void createRequestRejectsAmountOneCentBelowMinimum() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request(month, "9.99")
                )
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("below the minimum"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.00", "10.01", "19.99", "20.00"})
    void createRequestAcceptsAmountsWithinMinimumAndWithdrawableBoundaries(String amount) {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        var response = service.createRequest(
                new UserPrincipal(translator),
                request(month, amount)
        );

        assertEquals(CreatorPayoutStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal(amount).setScale(2), response.getAmount());
    }

    @Test
    void createRequestRejectsAmountOneCentAboveWithdrawableBalance() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request(month, "20.01")
                )
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("exceeds the withdrawable"));
    }

    @ParameterizedTest
    @EnumSource(
            value = CreatorPayoutStatus.class,
            names = {"PENDING", "APPROVED", "PROCESSING"}
    )
    void createRequestRejectsWhenActiveReservationExistsForSameMonth(
            CreatorPayoutStatus activeStatus
    ) {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        CreatorPayoutRequestEntity existing = payout(
                UUID.randomUUID(),
                CreatorPayoutRole.TRANSLATOR,
                activeStatus
        );
        existing.setPayoutMonth(month);

        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of(existing)
        );

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createRequest(
                        new UserPrincipal(translator),
                        request(month, "25.00")
                )
        );

        assertEquals(409, error.getCode());
    }

    @ParameterizedTest
    @EnumSource(
            value = CreatorPayoutStatus.class,
            names = {"REJECTED", "FAILED"}
    )
    void createRequestAllowsRetryAfterRetryablePreviousRequest(
            CreatorPayoutStatus previousStatus
    ) {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        CreatorPayoutRequestEntity existing = payout(
                UUID.randomUUID(),
                CreatorPayoutRole.TRANSLATOR,
                previousStatus
        );
        existing.setPayoutMonth(month);

        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of(existing)
        );

        var response = service.createRequest(
                new UserPrincipal(translator),
                request(month, "25.00")
        );

        assertEquals(CreatorPayoutStatus.PENDING, response.getStatus());
    }

    @Test
    void createRequestHappyPathCreatesPendingTranslatorPayoutSnapshot() {
        UserEntity translator = user("TRANSLATOR");
        translator.setFullName("  Creator Name  ");
        String month = lastClosedMonth();

        stubTranslatorCreateContext(
                translator,
                month,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        CreatePayoutRequest request = CreatePayoutRequest.builder()
                .payoutMonth(month)
                .requestedAmount(new BigDecimal("25.00"))
                .payoutCurrency("USD")
                .note("  monthly payout  ")
                .build();

        var response = service.createRequest(new UserPrincipal(translator), request);

        ArgumentCaptor<CreatorPayoutRequestEntity> captor =
                ArgumentCaptor.forClass(CreatorPayoutRequestEntity.class);
        verify(payoutRequestRepository).save(captor.capture());

        CreatorPayoutRequestEntity saved = captor.getValue();
        assertEquals(translator.getId(), saved.getUserId());
        assertEquals("Creator Name", saved.getUserName());
        assertEquals(CreatorPayoutRole.TRANSLATOR, saved.getRole());
        assertEquals(month, saved.getPayoutMonth());
        assertEquals(new BigDecimal("25.00"), saved.getAmount());
        assertEquals(new BigDecimal("25.00"), saved.getBaseAmountUsd());
        assertEquals("USD", saved.getCurrency());
        assertEquals(CreatorPayoutStatus.PENDING, saved.getStatus());
        assertEquals("monthly payout", saved.getRequestNote());
        assertNotNull(saved.getRequestedAt());
        assertEquals(CreatorPayoutStatus.PENDING, response.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRequestActiveAuthorCreatesPendingPayoutFromViewAndFollowRevenue() {
        UserEntity author = user("AUTHOR");
        author.setFullName("  Active Author  ");
        String month = lastClosedMonth();

        CreatorPayoutAccountEntity account = readyAccount(author.getId(), "USD");

        when(userRepository.findByIdWithRole(author.getId()))
                .thenReturn(Optional.of(author));
        when(payoutProfileService.requireReadyProfile(author.getId()))
                .thenReturn(account);
        when(payoutSettingsService.resolveCurrency("USD"))
                .thenReturn(resolvedUsd());
        when(payoutSettingsService.currentSettings())
                .thenReturn(settings(new BigDecimal("10.00")));
        when(payoutSettingsService.normalizeMoney(new BigDecimal("25.00")))
                .thenReturn(new BigDecimal("25.00"));
        when(payoutSettingsService.convertCurrencyToUsd(
                eq(new BigDecimal("25.00")),
                any(CreatorPayoutSettingsService.ResolvedCurrency.class)))
                .thenReturn(new BigDecimal("25.00"));
        when(payoutRequestRepository
                .findAllByUserIdAndPayoutMonthAndDeletedFalseOrderByCreatedAtDesc(
                        author.getId(),
                        month))
                .thenReturn(List.of());

        AuthorComicRevenueResponse revenue = AuthorComicRevenueResponse.builder()
                .comicId(UUID.randomUUID())
                .comicTitle("Author Comic")
                .monthlyViews(1000L)
                .viewUnits(BigDecimal.valueOf(1L))
                .viewRevenueUsd(new BigDecimal("40.00"))
                .monthlyFollows(100L)
                .followUnits(BigDecimal.valueOf(1L))
                .followRevenueUsd(new BigDecimal("40.00"))
                .totalRevenueUsd(new BigDecimal("80.00"))
                .build();

        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(List.of(revenue));

        var response = service.createRequest(
                new UserPrincipal(author),
                request(month, "25.00")
        );

        ArgumentCaptor<CreatorPayoutRequestEntity> captor =
                ArgumentCaptor.forClass(CreatorPayoutRequestEntity.class);
        verify(payoutRequestRepository).save(captor.capture());

        CreatorPayoutRequestEntity saved = captor.getValue();
        assertEquals(author.getId(), saved.getUserId());
        assertEquals("Active Author", saved.getUserName());
        assertEquals(CreatorPayoutRole.AUTHOR, saved.getRole());
        assertEquals(month, saved.getPayoutMonth());
        assertEquals(new BigDecimal("25.00"), saved.getAmount());
        assertEquals(new BigDecimal("25.00"), saved.getBaseAmountUsd());
        assertEquals(new BigDecimal("80.00"), saved.getGrossAmountUsd());
        assertEquals(new BigDecimal("480.00"), saved.getMonthlyLimitUsd());
        assertEquals(CreatorPayoutStatus.PENDING, saved.getStatus());
        assertEquals(account.getStripeConnectedAccountId(), saved.getStripeConnectedAccountId());
        assertEquals(CreatorPayoutStatus.PENDING, response.getStatus());

        verify(authorLicenseService).assertAuthorPayoutAllowed(author.getId());
    }

    // ===== approve: State Transition / account snapshot =====

    @Test
    void approvePendingTranslatorRequestMovesToApprovedAndRefreshesAccountSnapshot() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PENDING);
        CreatorPayoutAccountEntity account = readyAccount(payout.getUserId(), "USD");

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutProfileService.requireReadyProfile(payout.getUserId())).thenReturn(account);

        var response = service.approve(payoutId, "  approved manually  ");

        assertEquals(CreatorPayoutStatus.APPROVED, payout.getStatus());
        assertEquals("approved manually", payout.getAdminNote());
        assertNotNull(payout.getApprovedAt());
        assertEquals(account.getStripeConnectedAccountId(), payout.getStripeConnectedAccountId());
        assertEquals("VN", payout.getAccountCountry());
        assertEquals(CreatorPayoutStatus.APPROVED, response.getStatus());
    }

    @ParameterizedTest
    @EnumSource(
            value = CreatorPayoutStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"PENDING"}
    )
    void approveRejectsEveryInvalidSourceStatus(CreatorPayoutStatus status) {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, status);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.approve(payoutId, null)
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(payoutProfileService);
    }

    @Test
    void approveRejectsPayoutAccountCurrencyDrift() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PENDING);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutProfileService.requireReadyProfile(payout.getUserId()))
                .thenReturn(readyAccount(payout.getUserId(), "EUR"));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.approve(payoutId, null)
        );

        assertEquals(409, error.getCode());
        assertEquals(CreatorPayoutStatus.PENDING, payout.getStatus());
    }

    @Test
    void approveAuthorRequestRequiresActiveLicense() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.AUTHOR, CreatorPayoutStatus.PENDING);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        doThrow(new CustomException(
                403,
                "license inactive",
                org.springframework.http.HttpStatus.FORBIDDEN
        )).when(authorLicenseService).assertAuthorPayoutAllowed(payout.getUserId());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.approve(payoutId, null)
        );

        assertEquals(403, error.getCode());
        verifyNoInteractions(payoutProfileService);
    }

    // ===== reject: State Transition =====

    @ParameterizedTest
    @EnumSource(value = CreatorPayoutStatus.class, names = {"PENDING", "APPROVED"})
    void rejectAllowsPendingOrApprovedAndRecordsReason(CreatorPayoutStatus initialStatus) {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, initialStatus);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));

        var response = service.reject(payoutId, "  bank details invalid  ");

        assertEquals(CreatorPayoutStatus.REJECTED, payout.getStatus());
        assertEquals("bank details invalid", payout.getAdminNote());
        assertNotNull(payout.getRejectedAt());
        assertEquals(CreatorPayoutStatus.REJECTED, response.getStatus());
    }

    @ParameterizedTest
    @EnumSource(
            value = CreatorPayoutStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"PENDING", "APPROVED"}
    )
    void rejectRejectsEveryInvalidSourceStatus(CreatorPayoutStatus initialStatus) {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, initialStatus);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.reject(payoutId, "reason")
        );

        assertEquals(409, error.getCode());
    }

    // ===== payWithStripe: State Transition / idempotency / gateway =====

    @Test
    void payWithStripeApprovedRequestProcessesTransferAndMarksPaid() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);
        CreatorPayoutAccountEntity account = readyAccount(payout.getUserId(), "USD");

        stubPayContext(payoutId, payout, account);

        when(stripeGatewayService.createTransfer(
                eq(account.getStripeConnectedAccountId()),
                eq(new BigDecimal("25.00")),
                eq("USD"),
                eq(payoutId),
                eq(payout.getUserId()),
                eq("2026-07")))
                .thenReturn(mapper.readTree("{\"id\":\"tr_test_123\"}"));

        var response = service.payWithStripe(payoutId);

        assertEquals(CreatorPayoutStatus.PAID, payout.getStatus());
        assertEquals("tr_test_123", payout.getStripeTransferId());
        assertNotNull(payout.getPaidAt());
        assertEquals(CreatorPayoutStatus.PAID, response.getStatus());
    }

    @Test
    void payWithStripePersistsProcessingBeforeCallingStripe() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);
        CreatorPayoutAccountEntity account = readyAccount(payout.getUserId(), "USD");

        stubPayContext(payoutId, payout, account);

        AtomicReference<CreatorPayoutStatus> statusAtFlush = new AtomicReference<>();
        when(payoutRequestRepository.saveAndFlush(payout)).thenAnswer(invocation -> {
            statusAtFlush.set(payout.getStatus());
            return payout;
        });

        when(stripeGatewayService.createTransfer(
                anyString(), any(), anyString(), any(), any(), anyString()))
                .thenReturn(mapper.readTree("{\"id\":\"tr_processing\"}"));

        service.payWithStripe(payoutId);

        assertEquals(CreatorPayoutStatus.PROCESSING, statusAtFlush.get());

        InOrder order = inOrder(payoutRequestRepository, stripeGatewayService);
        order.verify(payoutRequestRepository).saveAndFlush(payout);
        order.verify(stripeGatewayService).createTransfer(
                anyString(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void payWithStripeMissingTransferIdMarksFailedAndRethrowsGatewayError() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);

        stubPayContext(
                payoutId,
                payout,
                readyAccount(payout.getUserId(), "USD")
        );

        when(stripeGatewayService.createTransfer(
                anyString(), any(), anyString(), any(), any(), anyString()))
                .thenReturn(mapper.readTree("{}"));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.payWithStripe(payoutId)
        );

        assertEquals(502, error.getCode());
        assertEquals(CreatorPayoutStatus.FAILED, payout.getStatus());
        assertNotNull(payout.getFailedAt());
        assertTrue(payout.getFailureReason().contains("transfer ID"));
        verify(payoutRequestRepository).save(payout);
    }

    @Test
    void payWithStripeRuntimeGatewayFailureMarksFailedAndReturns502() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);

        stubPayContext(
                payoutId,
                payout,
                readyAccount(payout.getUserId(), "USD")
        );

        when(stripeGatewayService.createTransfer(
                anyString(), any(), anyString(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("stripe unavailable"));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.payWithStripe(payoutId)
        );

        assertEquals(502, error.getCode());
        assertEquals(CreatorPayoutStatus.FAILED, payout.getStatus());
        assertNotNull(payout.getFailedAt());
        assertTrue(payout.getFailureReason().contains("stripe unavailable"));
        verify(payoutRequestRepository).save(payout);
    }

    @Test
    void payWithStripePaidWithTransferIdIsIdempotentAndDoesNotCallStripeAgain() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PAID);
        payout.setStripeTransferId("tr_existing");

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));

        var response = service.payWithStripe(payoutId);

        assertEquals("tr_existing", response.getStripeTransferId());
        verifyNoInteractions(payoutProfileService, payoutSettingsService, stripeGatewayService);
    }

    @ParameterizedTest
    @EnumSource(
            value = CreatorPayoutStatus.class,
            names = {"PENDING", "PROCESSING", "REJECTED", "PAID"}
    )
    void payWithStripeRejectsInvalidSourceStatusWithoutTransfer(
            CreatorPayoutStatus initialStatus
    ) {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, initialStatus);
        payout.setStripeTransferId(null);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.payWithStripe(payoutId)
        );

        assertEquals(409, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void payWithStripeRetriesFailedRequestSuccessfully() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.FAILED);
        payout.setFailureReason("previous failure");
        payout.setFailedAt(java.time.Instant.now());

        stubPayContext(
                payoutId,
                payout,
                readyAccount(payout.getUserId(), "USD")
        );

        when(stripeGatewayService.createTransfer(
                anyString(), any(), anyString(), any(), any(), anyString()))
                .thenReturn(mapper.readTree("{\"id\":\"tr_retry\"}"));

        var response = service.payWithStripe(payoutId);

        assertEquals(CreatorPayoutStatus.PAID, response.getStatus());
        assertEquals("tr_retry", payout.getStripeTransferId());
        assertNull(payout.getFailureReason());
        assertNull(payout.getFailedAt());
    }

    @Test
    void payWithStripeAuthorRequestRequiresActiveLicense() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.AUTHOR, CreatorPayoutStatus.APPROVED);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        doThrow(new CustomException(
                403,
                "license inactive",
                org.springframework.http.HttpStatus.FORBIDDEN
        )).when(authorLicenseService).assertAuthorPayoutAllowed(payout.getUserId());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.payWithStripe(payoutId)
        );

        assertEquals(403, error.getCode());
        verifyNoInteractions(payoutProfileService, stripeGatewayService);
    }

    @Test
    void payWithStripeRejectsCurrencyDriftBeforeCallingStripe() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);

        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutProfileService.requireReadyProfile(payout.getUserId()))
                .thenReturn(readyAccount(payout.getUserId(), "EUR"));

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.payWithStripe(payoutId)
        );

        assertEquals(409, error.getCode());
        assertEquals(CreatorPayoutStatus.APPROVED, payout.getStatus());
        verifyNoInteractions(stripeGatewayService);
    }

    @Test
    void payWithStripeRebuildsMissingAmountFromBaseUsd() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);
        payout.setAmount(null);

        CreatorPayoutAccountEntity account = readyAccount(payout.getUserId(), "USD");
        stubPayContext(payoutId, payout, account);
        when(payoutSettingsService.convertUsdToCurrency(
                eq(new BigDecimal("25.00")),
                any(CreatorPayoutSettingsService.ResolvedCurrency.class)))
                .thenReturn(new BigDecimal("25.00"));
        when(stripeGatewayService.createTransfer(
                eq(account.getStripeConnectedAccountId()),
                eq(new BigDecimal("25.00")),
                eq("USD"),
                eq(payoutId),
                eq(payout.getUserId()),
                eq(payout.getPayoutMonth())))
                .thenReturn(mapper.readTree("{\"id\":\"tr_amount\"}"));

        service.payWithStripe(payoutId);

        assertEquals(new BigDecimal("25.00"), payout.getAmount());
    }

    @Test
    void payWithStripeRestoresMissingUnitsPerUsdFromResolvedCurrency() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout =
                payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);
        payout.setPayoutUnitsPerUsd(null);

        stubPayContext(
                payoutId,
                payout,
                readyAccount(payout.getUserId(), "USD")
        );

        when(stripeGatewayService.createTransfer(
                anyString(), any(), anyString(), any(), any(), anyString()))
                .thenReturn(mapper.readTree("{\"id\":\"tr_units\"}"));

        service.payWithStripe(payoutId);

        assertEquals(new BigDecimal("1.000000"), payout.getPayoutUnitsPerUsd());
    }

    // ===== getOverview: Decision Table =====

    @Test
    void getOverviewIsNotRequestableForFutureMonth() {
        UserEntity translator = user("TRANSLATOR");
        String futureMonth = YearMonth.parse(currentMonth()).plusMonths(1).toString();

        stubTranslatorOverviewContext(
                translator,
                futureMonth,
                readyAccount(translator.getId(), "USD"),
                true,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        var response = service.getOverview(new UserPrincipal(translator), futureMonth);

        assertFalse(Boolean.TRUE.equals(response.getRequestable()));
        assertTrue(response.getNotRequestableReason().contains("completed months"));
    }

    @Test
    void getOverviewIsNotRequestableWithoutPayoutAccount() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();

        stubTranslatorOverviewContext(
                translator,
                month,
                null,
                false,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        var response = service.getOverview(new UserPrincipal(translator), month);

        assertFalse(Boolean.TRUE.equals(response.getRequestable()));
        assertTrue(response.getNotRequestableReason().contains("Stripe payout method"));
    }

    @Test
    void getOverviewIsNotRequestableWhenStripeAccountIsNotReady() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        CreatorPayoutAccountEntity account = readyAccount(translator.getId(), "USD");

        stubTranslatorOverviewContext(
                translator,
                month,
                account,
                false,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        var response = service.getOverview(new UserPrincipal(translator), month);

        assertFalse(Boolean.TRUE.equals(response.getRequestable()));
        assertTrue(response.getNotRequestableReason().contains("Complete Stripe onboarding"));
    }

    @ParameterizedTest
    @EnumSource(
            value = CreatorPayoutStatus.class,
            names = {"PENDING", "APPROVED", "PROCESSING"}
    )
    void getOverviewIsNotRequestableWhenActiveRequestExists(
            CreatorPayoutStatus activeStatus
    ) {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();
        CreatorPayoutRequestEntity activeRequest = payout(
                UUID.randomUUID(),
                CreatorPayoutRole.TRANSLATOR,
                activeStatus
        );
        activeRequest.setUserId(translator.getId());
        activeRequest.setPayoutMonth(month);

        stubTranslatorOverviewContext(
                translator,
                month,
                readyAccount(translator.getId(), "USD"),
                true,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of(activeRequest)
        );

        var response = service.getOverview(new UserPrincipal(translator), month);

        assertFalse(Boolean.TRUE.equals(response.getRequestable()));
        assertTrue(response.getNotRequestableReason().contains("still pending"));
    }

    @ParameterizedTest
    @EnumSource(
            value = CreatorPayoutStatus.class,
            names = {"REJECTED", "FAILED"}
    )
    void getOverviewAllowsRetryAfterRetryablePreviousRequest(
            CreatorPayoutStatus previousStatus
    ) {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();

        CreatorPayoutRequestEntity previous = payout(
                UUID.randomUUID(),
                CreatorPayoutRole.TRANSLATOR,
                previousStatus
        );
        previous.setUserId(translator.getId());
        previous.setPayoutMonth(month);

        stubTranslatorOverviewContext(
                translator,
                month,
                readyAccount(translator.getId(), "USD"),
                true,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of(previous)
        );

        var response = service.getOverview(new UserPrincipal(translator), month);

        assertTrue(Boolean.TRUE.equals(response.getRequestable()));
        assertNull(response.getNotRequestableReason());
        assertNotNull(response.getExistingRequest());
        assertEquals(previousStatus, response.getExistingRequest().getStatus());
    }

    @Test
    void getOverviewIsNotRequestableBelowMinimumBalance() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();

        stubTranslatorOverviewContext(
                translator,
                month,
                readyAccount(translator.getId(), "USD"),
                true,
                new BigDecimal("9.99"),
                new BigDecimal("10.00"),
                List.of()
        );

        var response = service.getOverview(new UserPrincipal(translator), month);

        assertFalse(Boolean.TRUE.equals(response.getRequestable()));
        assertTrue(response.getNotRequestableReason().contains("below the minimum"));
    }

    @Test
    void getOverviewIsRequestableWhenAllTranslatorConditionsPass() {
        UserEntity translator = user("TRANSLATOR");
        String month = lastClosedMonth();

        stubTranslatorOverviewContext(
                translator,
                month,
                readyAccount(translator.getId(), "USD"),
                true,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                List.of()
        );

        var response = service.getOverview(new UserPrincipal(translator), month);

        assertTrue(Boolean.TRUE.equals(response.getRequestable()));
        assertNull(response.getNotRequestableReason());
        assertEquals(CreatorPayoutRole.TRANSLATOR, response.getRole());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getOverviewAuthorLicenseOverridesOtherwiseRequestableState() {
        UserEntity author = user("AUTHOR");
        String month = lastClosedMonth();
        CreatorPayoutAccountEntity account = readyAccount(author.getId(), "USD");

        when(userRepository.findByIdWithRole(author.getId())).thenReturn(Optional.of(author));
        when(payoutSettingsService.currentSettings()).thenReturn(settings(new BigDecimal("10.00")));
        when(payoutProfileService.findEntity(author.getId())).thenReturn(account);
        when(payoutSettingsService.resolveCurrency("USD")).thenReturn(resolvedUsd());
        when(payoutRequestRepository.findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(author.getId()))
                .thenReturn(List.of());
        when(payoutProfileService.isReady(account)).thenReturn(true);
        when(payoutSettingsService.getSupportedCurrencies()).thenReturn(List.of());
        lenient().when(payoutSettingsService.convertUsdToCurrency(
                        any(BigDecimal.class),
                        any(CreatorPayoutSettingsService.ResolvedCurrency.class)))
                .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0)).setScale(2));

        AuthorComicRevenueResponse revenue = AuthorComicRevenueResponse.builder()
                .comicId(UUID.randomUUID())
                .comicTitle("Comic")
                .monthlyViews(1000L)
                .viewUnits(BigDecimal.valueOf(1L))
                .viewRevenueUsd(new BigDecimal("40.00"))
                .monthlyFollows(100L)
                .followUnits(BigDecimal.valueOf(1L))
                .followRevenueUsd(new BigDecimal("40.00"))
                .totalRevenueUsd(new BigDecimal("80.00"))
                .build();

        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(List.of(revenue));

        when(authorLicenseService.isAuthorPayoutAllowed(author.getId())).thenReturn(false);

        var response = service.getOverview(new UserPrincipal(author), month);

        assertFalse(Boolean.TRUE.equals(response.getRequestable()));
        assertTrue(response.getNotRequestableReason().contains("license is verified"));
    }

    // ===== getAdminPayouts =====

    @Test
    void getAdminPayoutsClampsNegativePageToZero() {
        when(payoutRequestRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(payoutRequestRepository.findAll()).thenReturn(List.of());

        service.getAdminPayouts(null, -5, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(payoutRequestRepository).findAll(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @ParameterizedTest
    @CsvSource({
            "0,1",
            "1,1",
            "2,2",
            "99,99",
            "100,100",
            "101,100"
    })
    void getAdminPayoutsAppliesPageSizeBoundaries(int requestedSize, int expectedSize) {
        when(payoutRequestRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(payoutRequestRepository.findAll()).thenReturn(List.of());

        service.getAdminPayouts(null, 0, requestedSize);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(payoutRequestRepository).findAll(captor.capture());
        assertEquals(expectedSize, captor.getValue().getPageSize());
    }

    @Test
    void getAdminPayoutsBuildsCountsAndUsdTotals() {
        CreatorPayoutRequestEntity pending =
                payout(UUID.randomUUID(), CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PENDING);
        CreatorPayoutRequestEntity paid =
                payout(UUID.randomUUID(), CreatorPayoutRole.AUTHOR, CreatorPayoutStatus.PAID);

        pending.setBaseAmountUsd(new BigDecimal("10.00"));
        paid.setBaseAmountUsd(new BigDecimal("40.00"));

        when(payoutRequestRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending, paid)));
        when(payoutRequestRepository.findAll()).thenReturn(List.of(pending, paid));

        var result = service.getAdminPayouts(null, 0, 20);

        assertEquals(1L, result.getCounts().get(CreatorPayoutStatus.PENDING));
        assertEquals(1L, result.getCounts().get(CreatorPayoutStatus.PAID));
        assertEquals(new BigDecimal("10.00"), result.getTotals().get(CreatorPayoutStatus.PENDING));
        assertEquals(new BigDecimal("40.00"), result.getTotals().get(CreatorPayoutStatus.PAID));
        assertEquals("USD", result.getTotalsCurrency());
    }

    @Test
    void getAdminPayoutsUsesStatusFilteredRepositoryWhenStatusProvided() {
        when(payoutRequestRepository.findAllByStatusAndDeletedFalse(
                eq(CreatorPayoutStatus.APPROVED),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(payoutRequestRepository.findAll()).thenReturn(List.of());

        service.getAdminPayouts(CreatorPayoutStatus.APPROVED, 0, 20);

        verify(payoutRequestRepository).findAllByStatusAndDeletedFalse(
                eq(CreatorPayoutStatus.APPROVED),
                any(Pageable.class));
        verify(payoutRequestRepository, never()).findAll(any(Pageable.class));
    }

    // ===== locked payout validation =====

    @Test
    void rejectRejectsNullPayoutId() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> service.reject(null, "x")
        );

        assertEquals(400, error.getCode());
        verifyNoInteractions(payoutRequestRepository);
    }

    @Test
    void rejectRejectsMissingPayoutId() {
        UUID id = UUID.randomUUID();
        when(payoutRequestRepository.findLockedById(id)).thenReturn(Optional.empty());

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.reject(id, "x")
        );

        assertEquals(404, error.getCode());
    }

    // ===== helpers =====

    private void stubTranslatorCreateContext(
            UserEntity translator,
            String month,
            BigDecimal cumulativeEarnedUsd,
            BigDecimal minimumUsd,
            List<CreatorPayoutRequestEntity> existingRequests
    ) {
        CreatorPayoutAccountEntity account = readyAccount(translator.getId(), "USD");

        lenient().when(userRepository.findByIdWithRole(translator.getId()))
                .thenReturn(Optional.of(translator));
        lenient().when(payoutProfileService.requireReadyProfile(translator.getId()))
                .thenReturn(account);
        lenient().when(payoutSettingsService.resolveCurrency("USD"))
                .thenReturn(resolvedUsd());
        lenient().when(payoutSettingsService.currentSettings())
                .thenReturn(settings(minimumUsd));

        lenient().when(payoutSettingsService.normalizeMoney(any(BigDecimal.class)))
                .thenAnswer(invocation ->
                        ((BigDecimal) invocation.getArgument(0)).setScale(2)
                );
        lenient().when(payoutSettingsService.convertCurrencyToUsd(
                        any(BigDecimal.class),
                        any(CreatorPayoutSettingsService.ResolvedCurrency.class)))
                .thenAnswer(invocation ->
                        ((BigDecimal) invocation.getArgument(0)).setScale(2)
                );

        lenient().when(translatorEarningRepository
                        .findAllByTranslatorIdAndEntryMonthOrderByCreatedAtAsc(
                                eq(translator.getId()),
                                anyString()))
                .thenReturn(List.of());
        lenient().when(translatorEarningRepository.sumAmountThroughMonth(
                        translator.getId(),
                        month))
                .thenReturn(cumulativeEarnedUsd);
        lenient().when(payoutRequestRepository.sumReservedThroughMonth(
                        eq(translator.getId()),
                        eq(month),
                        anyList()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(payoutRequestRepository
                        .findAllByUserIdAndPayoutMonthAndDeletedFalseOrderByCreatedAtDesc(
                                translator.getId(),
                                month))
                .thenReturn(existingRequests);
    }

    private void stubTranslatorOverviewContext(
            UserEntity translator,
            String selectedMonth,
            CreatorPayoutAccountEntity account,
            boolean accountReady,
            BigDecimal cumulativeEarnedUsd,
            BigDecimal minimumUsd,
            List<CreatorPayoutRequestEntity> requests
    ) {
        lenient().when(userRepository.findByIdWithRole(translator.getId()))
                .thenReturn(Optional.of(translator));
        lenient().when(payoutSettingsService.currentSettings())
                .thenReturn(settings(minimumUsd));
        lenient().when(payoutProfileService.findEntity(translator.getId()))
                .thenReturn(account);
        lenient().when(payoutSettingsService.resolveCurrency(
                        account == null ? "USD" : account.getCurrency()))
                .thenReturn(resolvedUsd());

        lenient().when(translatorEarningRepository
                        .findAllByTranslatorIdAndEntryMonthOrderByCreatedAtAsc(
                                eq(translator.getId()),
                                anyString()))
                .thenReturn(List.of());
        lenient().when(translatorEarningRepository.sumAmountThroughMonth(
                        translator.getId(),
                        selectedMonth))
                .thenReturn(cumulativeEarnedUsd);
        lenient().when(payoutRequestRepository.sumReservedThroughMonth(
                        eq(translator.getId()),
                        eq(selectedMonth),
                        anyList()))
                .thenReturn(BigDecimal.ZERO);

        lenient().when(payoutRequestRepository
                        .findAllByUserIdAndDeletedFalseOrderByCreatedAtDesc(translator.getId()))
                .thenReturn(requests);

        if (account != null) {
            lenient().when(payoutProfileService.isReady(account)).thenReturn(accountReady);
        }

        lenient().when(payoutSettingsService.getSupportedCurrencies()).thenReturn(List.of());
        lenient().when(payoutSettingsService.convertUsdToCurrency(
                        any(BigDecimal.class),
                        any(CreatorPayoutSettingsService.ResolvedCurrency.class)))
                .thenAnswer(invocation ->
                        ((BigDecimal) invocation.getArgument(0)).setScale(2)
                );
    }

    private void stubPayContext(
            UUID payoutId,
            CreatorPayoutRequestEntity payout,
            CreatorPayoutAccountEntity account
    ) {
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutProfileService.requireReadyProfile(payout.getUserId())).thenReturn(account);
        when(payoutSettingsService.resolveCurrency(payout.getCurrency())).thenReturn(resolvedUsd());
    }

    private CreatePayoutRequest request(String month, String amount) {
        return CreatePayoutRequest.builder()
                .payoutMonth(month)
                .requestedAmount(new BigDecimal(amount))
                .payoutCurrency("USD")
                .build();
    }

    private CreatorPayoutSettingEntity settings(BigDecimal minimumUsd) {
        return CreatorPayoutSettingEntity.builder()
                .minimumPayoutUsd(minimumUsd)
                .translatorTaskRateUsd(new BigDecimal("1.20"))
                .translatorMonthlyLimitUsd(new BigDecimal("200.00"))
                .authorViewsPerUnit(1000L)
                .authorViewUnitRateUsd(new BigDecimal("40.00"))
                .authorFollowsPerUnit(100L)
                .authorFollowUnitRateUsd(new BigDecimal("40.00"))
                .authorMonthlyLimitUsd(new BigDecimal("480.00"))
                .currency("USD")
                .build();
    }

    private String currentMonth() {
        return YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
    }

    private String lastClosedMonth() {
        return YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .minusMonths(1)
                .toString();
    }

    private CreatorPayoutRequestEntity payout(
            UUID id,
            CreatorPayoutRole role,
            CreatorPayoutStatus status
    ) {
        CreatorPayoutRequestEntity payout = CreatorPayoutRequestEntity.builder()
                .userId(UUID.randomUUID())
                .userName("Creator")
                .userEmail("creator@example.com")
                .role(role)
                .payoutMonth("2026-07")
                .amount(new BigDecimal("25.00"))
                .baseAmountUsd(new BigDecimal("25.00"))
                .grossAmountUsd(new BigDecimal("30.00"))
                .monthlyLimitUsd(new BigDecimal("200.00"))
                .payoutUnitsPerUsd(new BigDecimal("1.000000"))
                .accountCountry("VN")
                .currency("USD")
                .status(status)
                .stripeConnectedAccountId("acct_test")
                .build();
        payout.setId(id);
        return payout;
    }

    private CreatorPayoutAccountEntity readyAccount(UUID userId, String currency) {
        return CreatorPayoutAccountEntity.builder()
                .userId(userId)
                .stripeConnectedAccountId("acct_ready")
                .accountCountry("VN")
                .currency(currency)
                .active(true)
                .detailsSubmitted(true)
                .payoutsEnabled(true)
                .transfersCapability("active")
                .externalAccountLast4("4242")
                .build();
    }

    private CreatorPayoutSettingsService.ResolvedCurrency resolvedUsd() {
        return new CreatorPayoutSettingsService.ResolvedCurrency(
                CreatorPayoutCurrency.USD,
                new BigDecimal("1.000000")
        );
    }

    private UserEntity user(String role) {
        UserEntity user = UserEntity.builder()
                .username("creator")
                .email("creator@example.com")
                .role(RoleEntity.builder().roleName(role).build())
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }
}
