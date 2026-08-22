package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.dto.request.CreateStripePayoutOnboardingRequest;
import com.sep.comiverse.entity.CreatorPayoutAccountEntity;
import com.sep.comiverse.entity.RoleEntity;
import com.sep.comiverse.entity.UserEntity;
import com.sep.comiverse.entity.enums.CreatorPayoutCurrency;
import com.sep.comiverse.entity.enums.CreatorPayoutRole;
import com.sep.comiverse.entity.enums.StripePayoutProfileStatus;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.repository.ICreatorPayoutAccountRepository;
import com.sep.comiverse.repository.IUserRepository;
import com.sep.comiverse.security.UserPrincipal;
import com.sep.comiverse.service.CreatorPayoutAccountService;
import com.sep.comiverse.service.CreatorPayoutSettingsService;
import com.sep.comiverse.service.StripeGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorPayoutAccountServiceTest {

    @Mock private ICreatorPayoutAccountRepository profileRepository;
    @Mock private IUserRepository userRepository;
    @Mock private StripeGatewayService stripeGatewayService;
    @Mock private CreatorPayoutSettingsService payoutSettingsService;

    private CreatorPayoutAccountService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CreatorPayoutAccountService(
                profileRepository, userRepository, stripeGatewayService, payoutSettingsService);
        ReflectionTestUtils.setField(service, "defaultCountry", "VN");
        lenient().when(profileRepository.save(any(CreatorPayoutAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(profileRepository.saveAndFlush(any(CreatorPayoutAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void findEntity_nullIdReturnsNullWithoutRepositoryCall() {
        assertNull(service.findEntity(null));
        verifyNoInteractions(profileRepository);
    }

    @Test
    void getProfile_requiresAuthenticatedCreatorRole() {
        assertEquals(401, assertThrows(CustomException.class, () -> service.getProfile(null)).getCode());

        UserEntity reader = user("READER");
        when(userRepository.findByIdWithRole(reader.getId())).thenReturn(Optional.of(reader));
        assertEquals(403, assertThrows(CustomException.class,
                () -> service.getProfile(new UserPrincipal(reader))).getCode());
    }

    @Test
    void isReady_requiresAllStripeReadinessSignals() {
        CreatorPayoutAccountEntity profile = CreatorPayoutAccountEntity.builder()
                .active(true)
                .detailsSubmitted(true)
                .payoutsEnabled(true)
                .transfersCapability("active")
                .externalAccountLast4("4242")
                .build();
        assertTrue(service.isReady(profile));

        profile.setPayoutsEnabled(false);
        assertFalse(service.isReady(profile));
        assertFalse(service.isReady(null));
    }

    @Test
    void requireReadyProfile_handlesMissingIncompleteAndReadyProfiles() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.requireReadyProfile(userId)).getCode());

        CreatorPayoutAccountEntity profile = CreatorPayoutAccountEntity.builder()
                .userId(userId).currency("USD").active(true).build();
        when(profileRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(profile));
        assertEquals(400, assertThrows(CustomException.class,
                () -> service.requireReadyProfile(userId)).getCode());

        profile.setDetailsSubmitted(true);
        profile.setPayoutsEnabled(true);
        profile.setTransfersCapability("active");
        profile.setExternalAccountLast4("1234");
        when(payoutSettingsService.resolveCurrency("USD")).thenReturn(resolved("USD"));
        assertSame(profile, service.requireReadyProfile(userId));
    }

    @Test
    void createOnboardingLink_newAuthorAccount_createsStripeAccountAndLink() throws Exception {
        UserEntity author = user("AUTHOR");
        UserPrincipal principal = new UserPrincipal(author);
        CreateStripePayoutOnboardingRequest request = CreateStripePayoutOnboardingRequest.builder()
                .countryCode("vn")
                .payoutCurrency("USD")
                .build();
        when(userRepository.findByIdWithRole(author.getId())).thenReturn(Optional.of(author));
        when(payoutSettingsService.resolveCurrency(anyString()))
                .thenAnswer(invocation -> resolved(invocation.getArgument(0)));
        when(profileRepository.findByUserId(author.getId())).thenReturn(Optional.empty());
        JsonNode stripeAccount = mapper.readTree("""
                {"id":"acct_test123","livemode":false,"country":"VN","default_currency":"usd",
                 "metadata":{"user_id":"%s"},"details_submitted":false,"payouts_enabled":false,
                 "charges_enabled":false,"capabilities":{"transfers":"inactive"},
                 "requirements":{"currently_due":["individual.verification.document"]}}
                """.formatted(author.getId()));
        when(stripeGatewayService.createPayoutConnectedAccount(
                eq(author.getId()), eq(author.getEmail()), eq("VN"), eq("AUTHOR"), eq("USD")))
                .thenReturn(stripeAccount);
        when(stripeGatewayService.createPayoutAccountOnboardingLink(
                eq("acct_test123"), contains("/author/payout"), contains("/author/payout")))
                .thenReturn(mapper.readTree("{\"url\":\"https://connect.stripe.test/link\",\"expires_at\":1893456000}"));

        var response = service.createOnboardingLink(principal, request);

        assertEquals("https://connect.stripe.test/link", response.getOnboardingUrl());
        assertEquals("acct_test123", response.getAccount().getStripeConnectedAccountId());
        assertEquals(CreatorPayoutRole.AUTHOR, response.getAccount().getRole());
        assertEquals(StripePayoutProfileStatus.ONBOARDING, response.getAccount().getOnboardingStatus());
    }

    @Test
    void createOnboardingLink_rejectsNullRequestAfterAuthentication() {
        UserEntity translator = user("TRANSLATOR");
        when(userRepository.findByIdWithRole(translator.getId())).thenReturn(Optional.of(translator));

        assertEquals(400, assertThrows(CustomException.class,
                () -> service.createOnboardingLink(new UserPrincipal(translator), null)).getCode());
        verifyNoInteractions(stripeGatewayService);
    }

    @Test
    void createOnboardingLink_rejectsUnsupportedCurrencyBeforeStripeChanges() {
        UserEntity translator = user("TRANSLATOR");
        CreateStripePayoutOnboardingRequest request = CreateStripePayoutOnboardingRequest.builder()
                .countryCode("VN").payoutCurrency("EUR").build();
        when(userRepository.findByIdWithRole(translator.getId())).thenReturn(Optional.of(translator));
        when(payoutSettingsService.resolveCurrency("EUR"))
                .thenThrow(new CustomException(
                        400,
                        "Unsupported payout currency. Allowed value: USD",
                        org.springframework.http.HttpStatus.BAD_REQUEST
                ));

        assertEquals(400, assertThrows(CustomException.class,
                () -> service.createOnboardingLink(new UserPrincipal(translator), request)).getCode());
        verifyNoInteractions(profileRepository, stripeGatewayService);
    }

    @Test
    void syncFromWebhook_updatesMatchingSandboxProfileAndRejectsWrongOwnerMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatorPayoutAccountEntity profile = CreatorPayoutAccountEntity.builder()
                .userId(userId).stripeConnectedAccountId("acct_123").currency("USD").active(true).build();
        when(profileRepository.findByStripeConnectedAccountIdAndDeletedFalse("acct_123"))
                .thenReturn(Optional.of(profile));
        when(payoutSettingsService.resolveCurrency("usd")).thenReturn(resolved("USD"));

        JsonNode ready = mapper.readTree("""
                {"id":"acct_123","livemode":false,"country":"VN","default_currency":"usd",
                 "metadata":{"user_id":"%s"},"details_submitted":true,"payouts_enabled":true,
                 "charges_enabled":false,"capabilities":{"transfers":"active"},
                 "requirements":{"currently_due":[]},
                 "external_accounts":{"data":[{"object":"bank_account","last4":"6789","bank_name":"Test Bank"}]}}
                """.formatted(userId));
        service.syncFromAccountUpdatedWebhook(ready);

        assertEquals(StripePayoutProfileStatus.READY, profile.getOnboardingStatus());
        assertEquals("6789", profile.getExternalAccountLast4());
        verify(profileRepository).save(profile);

        JsonNode wrongOwner = mapper.readTree("""
                {"id":"acct_123","livemode":false,"metadata":{"user_id":"%s"}}
                """.formatted(UUID.randomUUID()));
        assertEquals(403, assertThrows(CustomException.class,
                () -> service.syncFromAccountUpdatedWebhook(wrongOwner)).getCode());
    }

    private CreatorPayoutSettingsService.ResolvedCurrency resolved(String code) {
        return new CreatorPayoutSettingsService.ResolvedCurrency(
                CreatorPayoutCurrency.fromCode(code), BigDecimal.ONE.setScale(6));
    }

    private UserEntity user(String roleName) {
        UserEntity user = UserEntity.builder()
                .username(roleName.toLowerCase())
                .email(roleName.toLowerCase() + "@example.com")
                .fullName(roleName + " User")
                .role(RoleEntity.builder().roleName(roleName).build())
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }
}
