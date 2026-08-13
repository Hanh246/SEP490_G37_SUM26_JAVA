package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.CreatePayoutRequest;
import com.sep.comiverse.entity.CreatorPayoutAccountEntity;
import com.sep.comiverse.entity.CreatorPayoutRequestEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutCurrency;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.CreatorPayoutStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.*;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    void createRequest_requiresAuthenticatedCreatorAndValidClosedMonth() {
        CreatePayoutRequest request = CreatePayoutRequest.builder()
                .payoutMonth("bad-month").requestedAmount(BigDecimal.TEN).payoutCurrency("USD").build();
        assertEquals(401, assertThrows(CustomException.class,
                () -> service.createRequest(null, request)).getCode());

        UserEntity translator = user("TRANSLATOR");
        when(userRepository.findByIdWithRole(translator.getId())).thenReturn(Optional.of(translator));
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.createRequest(new UserPrincipal(translator), request)).getCode());
    }

    @Test
    void createRequest_authorMustPassLicenseGateBeforePayoutCalculation() {
        UserEntity author = user("AUTHOR");
        when(userRepository.findByIdWithRole(author.getId())).thenReturn(Optional.of(author));
        doThrow(new CustomException(403, "license inactive", org.springframework.http.HttpStatus.FORBIDDEN))
                .when(authorLicenseService).assertAuthorPayoutAllowed(author.getId());
        CreatePayoutRequest request = CreatePayoutRequest.builder()
                .payoutMonth("2026-07").requestedAmount(BigDecimal.TEN).payoutCurrency("USD").build();

        assertEquals(403, assertThrows(CustomException.class,
                () -> service.createRequest(new UserPrincipal(author), request)).getCode());
        verifyNoInteractions(payoutProfileService);
    }

    @Test
    void approve_pendingTranslatorRequest_movesToApprovedAndRefreshesAccountSnapshot() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout = payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PENDING);
        CreatorPayoutAccountEntity account = readyAccount(payout.getUserId(), "USD");
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutProfileService.requireReadyProfile(payout.getUserId())).thenReturn(account);

        var response = service.approve(payoutId, "  approved manually  ");

        assertEquals(CreatorPayoutStatus.APPROVED, payout.getStatus());
        assertEquals("approved manually", payout.getAdminNote());
        assertNotNull(payout.getApprovedAt());
        assertEquals(account.getStripeConnectedAccountId(), payout.getStripeConnectedAccountId());
        assertEquals(CreatorPayoutStatus.APPROVED, response.getStatus());
    }

    @Test
    void approve_rejectsInvalidTransitionAndCurrencyDrift() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity paid = payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PAID);
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(paid));
        assertEquals(409, assertThrows(CustomException.class,
                () -> service.approve(payoutId, null)).getCode());

        CreatorPayoutRequestEntity pending = payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PENDING);
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(pending));
        when(payoutProfileService.requireReadyProfile(pending.getUserId()))
                .thenReturn(readyAccount(pending.getUserId(), "EUR"));
        assertEquals(409, assertThrows(CustomException.class,
                () -> service.approve(payoutId, null)).getCode());
    }

    @Test
    void reject_allowsPendingOrApprovedAndRecordsReason() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout = payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));

        var response = service.reject(payoutId, "  bank details invalid  ");

        assertEquals(CreatorPayoutStatus.REJECTED, payout.getStatus());
        assertEquals("bank details invalid", payout.getAdminNote());
        assertNotNull(payout.getRejectedAt());
        assertEquals(CreatorPayoutStatus.REJECTED, response.getStatus());
    }

    @Test
    void payWithStripe_approvedRequest_processesTransferAndMarksPaid() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout = payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);
        CreatorPayoutAccountEntity account = readyAccount(payout.getUserId(), "USD");
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutProfileService.requireReadyProfile(payout.getUserId())).thenReturn(account);
        when(payoutSettingsService.resolveCurrency("USD")).thenReturn(resolvedUsd());
        when(stripeGatewayService.createTransfer(
                eq(account.getStripeConnectedAccountId()), eq(new BigDecimal("25.00")), eq("USD"),
                eq(payoutId), eq(payout.getUserId()), eq("2026-07")))
                .thenReturn(mapper.readTree("{\"id\":\"tr_test_123\"}"));

        var response = service.payWithStripe(payoutId);

        assertEquals(CreatorPayoutStatus.PAID, payout.getStatus());
        assertEquals("tr_test_123", payout.getStripeTransferId());
        assertNotNull(payout.getPaidAt());
        verify(payoutRequestRepository).saveAndFlush(payout);
        assertEquals(CreatorPayoutStatus.PAID, response.getStatus());
    }

    @Test
    void payWithStripe_missingTransferId_marksFailedAndRethrowsGatewayError() throws Exception {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout = payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.APPROVED);
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutProfileService.requireReadyProfile(payout.getUserId()))
                .thenReturn(readyAccount(payout.getUserId(), "USD"));
        when(payoutSettingsService.resolveCurrency("USD")).thenReturn(resolvedUsd());
        when(stripeGatewayService.createTransfer(anyString(), any(), anyString(), any(), any(), anyString()))
                .thenReturn(mapper.readTree("{}"));

        CustomException error = assertThrows(CustomException.class, () -> service.payWithStripe(payoutId));

        assertEquals(502, error.getCode());
        assertEquals(CreatorPayoutStatus.FAILED, payout.getStatus());
        assertNotNull(payout.getFailedAt());
        assertTrue(payout.getFailureReason().contains("transfer ID"));
    }

    @Test
    void payWithStripe_paidWithTransferId_isIdempotentAndDoesNotCallStripeAgain() {
        UUID payoutId = UUID.randomUUID();
        CreatorPayoutRequestEntity payout = payout(payoutId, CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PAID);
        payout.setStripeTransferId("tr_existing");
        when(payoutRequestRepository.findLockedById(payoutId)).thenReturn(Optional.of(payout));

        var response = service.payWithStripe(payoutId);

        assertEquals("tr_existing", response.getStripeTransferId());
        verifyNoInteractions(stripeGatewayService);
    }

    @Test
    void getAdminPayouts_clampsPaginationAndBuildsStatusTotals() {
        CreatorPayoutRequestEntity pending = payout(UUID.randomUUID(), CreatorPayoutRole.TRANSLATOR, CreatorPayoutStatus.PENDING);
        CreatorPayoutRequestEntity paid = payout(UUID.randomUUID(), CreatorPayoutRole.AUTHOR, CreatorPayoutStatus.PAID);
        paid.setBaseAmountUsd(new BigDecimal("40.00"));
        pending.setBaseAmountUsd(new BigDecimal("10.00"));
        when(payoutRequestRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending, paid)));
        when(payoutRequestRepository.findAll()).thenReturn(List.of(pending, paid));

        var result = service.getAdminPayouts(null, -5, 1000);

        assertEquals(2, result.getItems().size());
        assertEquals(1L, result.getCounts().get(CreatorPayoutStatus.PAID));
        assertEquals(new BigDecimal("40.00"), result.getTotals().get(CreatorPayoutStatus.PAID));
        assertEquals("USD", result.getTotalsCurrency());
    }

    @Test
    void lockedPayoutValidation_handlesNullAndMissingId() {
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.reject(null, "x")).getCode());
        UUID id = UUID.randomUUID();
        when(payoutRequestRepository.findLockedById(id)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(CustomException.class,
                () -> service.reject(id, "x")).getCode());
    }

    private CreatorPayoutRequestEntity payout(UUID id, CreatorPayoutRole role, CreatorPayoutStatus status) {
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
                CreatorPayoutCurrency.USD, new BigDecimal("1.000000"));
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
