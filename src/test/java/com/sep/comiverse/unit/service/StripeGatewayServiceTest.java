package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.entity.enums.BillingInterval;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.service.StripeGatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StripeGatewayServiceTest {

    private static final String API = "https://api.stripe.com";
    private static final String TEST_KEY = "sk_test_123";
    private static final String WEBHOOK_SECRET = "whsec_unit_test";

    private final ObjectMapper mapper = new ObjectMapper();

    // ===== configuration / sandbox key =====

    @Test
    void isConfiguredAcceptsSandboxSecretKey() {
        assertTrue(serviceOnly("  sk_test_123  ", WEBHOOK_SECRET).isConfigured());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "sk_live_123", "pk_test_123", "abc"})
    void isConfiguredRejectsMissingOrNonSandboxSecretKeys(String key) {
        assertFalse(serviceOnly(key, WEBHOOK_SECRET).isConfigured());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sk_live_123"})
    void stripeRequestRejectsMissingOrLiveSecretKeyBeforeNetworkCall(String key) {
        Harness harness = harness(key, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createBillingPortalSession("cus_123")
        );

        assertEquals(503, error.getCode());
        harness.server().verify();
    }

    // ===== product catalog =====

    @Test
    void createProductBuildsProductMetadataAndReturnsStripeId() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("VND", new BigDecimal("79000"));

        harness.server()
                .expect(requestTo(API + "/v1/products"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andExpect(formContains(Map.of(
                        "name", "Premium",
                        "description", "Premium monthly plan",
                        "metadata[plan_id]", plan.getId().toString(),
                        "metadata[plan_code]", "PREMIUM"
                )))
                .andRespond(withSuccess("{\"id\":\"prod_123\"}", MediaType.APPLICATION_JSON));

        assertEquals("prod_123", harness.service().createProduct(plan));
        harness.server().verify();
    }

    @Test
    void createProductOmitsBlankDescription() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("VND", new BigDecimal("79000"));
        plan.setDescription("   ");

        harness.server()
                .expect(requestTo(API + "/v1/products"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formLacks("description"))
                .andRespond(withSuccess("{\"id\":\"prod_123\"}", MediaType.APPLICATION_JSON));

        assertEquals("prod_123", harness.service().createProduct(plan));
        harness.server().verify();
    }

    @Test
    void createProductRejectsStripeResponseWithoutProductId() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("VND", new BigDecimal("79000"));

        harness.server()
                .expect(requestTo(API + "/v1/products"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createProduct(plan)
        );

        assertEquals(502, error.getCode());
        assertTrue(error.getMessage().contains("product ID"));
        harness.server().verify();
    }

    @Test
    void createProductUsesStableIdempotencyKeyForSamePlan() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("VND", new BigDecimal("79000"));
        List<String> keys = new ArrayList<>();

        harness.server()
                .expect(requestTo(API + "/v1/products"))
                .andExpect(captureHeader("Idempotency-Key", keys))
                .andRespond(withSuccess("{\"id\":\"prod_1\"}", MediaType.APPLICATION_JSON));
        harness.server()
                .expect(requestTo(API + "/v1/products"))
                .andExpect(captureHeader("Idempotency-Key", keys))
                .andRespond(withSuccess("{\"id\":\"prod_2\"}", MediaType.APPLICATION_JSON));

        harness.service().createProduct(plan);
        harness.service().createProduct(plan);

        assertEquals(2, keys.size());
        assertEquals(keys.get(0), keys.get(1));
        assertTrue(keys.get(0).startsWith("product-"));
        harness.server().verify();
    }

    @Test
    void createProductChangesIdempotencyKeyWhenPlanFingerprintChanges() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.00"));
        List<String> keys = new ArrayList<>();

        harness.server()
                .expect(requestTo(API + "/v1/products"))
                .andExpect(captureHeader("Idempotency-Key", keys))
                .andRespond(withSuccess("{\"id\":\"prod_1\"}", MediaType.APPLICATION_JSON));

        harness.service().createProduct(plan);

        plan.setPrice(new BigDecimal("12.00"));

        harness.server()
                .expect(requestTo(API + "/v1/products"))
                .andExpect(captureHeader("Idempotency-Key", keys))
                .andRespond(withSuccess("{\"id\":\"prod_2\"}", MediaType.APPLICATION_JSON));

        harness.service().createProduct(plan);

        assertEquals(2, keys.size());
        assertNotEquals(keys.get(0), keys.get(1));
        harness.server().verify();
    }

    @Test
    void updateProductBuildsExpectedStripeRequest() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.25"));
        plan.setStripeProductId("prod_123");
        plan.setDescription(null);

        harness.server()
                .expect(requestTo(API + "/v1/products/prod_123"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of(
                        "name", "Premium",
                        "description", "",
                        "metadata[plan_id]", plan.getId().toString(),
                        "metadata[plan_code]", "PREMIUM"
                )))
                .andExpect(headerMatchesPrefix("Idempotency-Key", "product-update-"))
                .andRespond(withSuccess("{\"id\":\"prod_123\"}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> harness.service().updateProduct(plan));
        harness.server().verify();
    }

    // ===== recurring price / public minor-unit conversion =====

    @Test
    void createRecurringPriceUsesZeroDecimalMinorUnitForVnd() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("VND", new BigDecimal("79000"));

        harness.server()
                .expect(requestTo(API + "/v1/prices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of(
                        "product", "prod_123",
                        "currency", "vnd",
                        "unit_amount", "79000",
                        "recurring[interval]", "month",
                        "recurring[interval_count]", "1"
                )))
                .andRespond(withSuccess("{\"id\":\"price_vnd\"}", MediaType.APPLICATION_JSON));

        assertEquals(
                "price_vnd",
                harness.service().createRecurringPrice(plan, "prod_123")
        );
        harness.server().verify();
    }

    @Test
    void createRecurringPriceUsesTwoDecimalMinorUnitForUsd() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.25"));

        harness.server()
                .expect(requestTo(API + "/v1/prices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of(
                        "product", "prod_123",
                        "currency", "usd",
                        "unit_amount", "1025"
                )))
                .andRespond(withSuccess("{\"id\":\"price_usd\"}", MediaType.APPLICATION_JSON));

        assertEquals(
                "price_usd",
                harness.service().createRecurringPrice(plan, "prod_123")
        );
        harness.server().verify();
    }

    @Test
    void createRecurringPriceRejectsTooManyDecimalPlaces() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.001"));

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createRecurringPrice(plan, "prod_123")
        );

        assertEquals(400, error.getCode());
        harness.server().verify();
    }

    @Test
    void createRecurringPriceRejectsFractionalAmountForZeroDecimalCurrency() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("VND", new BigDecimal("79000.5"));

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createRecurringPrice(plan, "prod_123")
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("decimal places"));
        harness.server().verify();
    }

    @Test
    void createRecurringPriceUsesYearIntervalFromPlan() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("99.00"));
        plan.setBillingInterval(BillingInterval.YEAR);
        plan.setIntervalCount(1);

        harness.server()
                .expect(requestTo(API + "/v1/prices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of(
                        "recurring[interval]", "year",
                        "recurring[interval_count]", "1",
                        "unit_amount", "9900"
                )))
                .andRespond(withSuccess("{\"id\":\"price_year\"}", MediaType.APPLICATION_JSON));

        assertEquals(
                "price_year",
                harness.service().createRecurringPrice(plan, "prod_123")
        );
        harness.server().verify();
    }

    @Test
    void createRecurringPriceRejectsStripeResponseWithoutPriceId() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.25"));

        harness.server()
                .expect(requestTo(API + "/v1/prices"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createRecurringPrice(plan, "prod_123")
        );

        assertEquals(502, error.getCode());
        assertTrue(error.getMessage().contains("price ID"));
        harness.server().verify();
    }

    // ===== checkout session =====

    @Test
    void createCheckoutSessionUsesExistingCustomerWhenProvided() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.25"));
        UUID userId = UUID.randomUUID();

        harness.server()
                .expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.ofEntries(
                        Map.entry("mode", "subscription"),
                        Map.entry("client_reference_id", userId.toString()),
                        Map.entry("customer", "cus_existing"),
                        Map.entry("line_items[0][price]", "price_123"),
                        Map.entry("line_items[0][quantity]", "1"),
                        Map.entry("metadata[user_id]", userId.toString()),
                        Map.entry("metadata[plan_id]", plan.getId().toString()),
                        Map.entry("metadata[plan_code]", "PREMIUM"),
                        Map.entry("subscription_data[metadata][user_id]", userId.toString()),
                        Map.entry("subscription_data[metadata][plan_id]", plan.getId().toString()),
                        Map.entry("subscription_data[metadata][plan_code]", "PREMIUM"),
                        Map.entry("billing_address_collection", "auto"),
                        Map.entry("payment_method_collection", "always")
                )))
                .andExpect(formLacks("customer_email"))
                .andRespond(withSuccess("{\"id\":\"cs_123\",\"url\":\"https://checkout\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createCheckoutSession(
                userId,
                "reader@example.com",
                plan,
                "cus_existing"
        );

        assertEquals("cs_123", response.path("id").asText());
        harness.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createCheckoutSessionUsesEmailWhenExistingCustomerIsMissing(String customerId) {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.25"));
        UUID userId = UUID.randomUUID();

        harness.server()
                .expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(formContains(Map.of(
                        "customer_email", "reader@example.com",
                        "client_reference_id", userId.toString()
                )))
                .andExpect(formLacks("customer"))
                .andRespond(withSuccess("{\"id\":\"cs_email\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createCheckoutSession(
                userId,
                "reader@example.com",
                plan,
                customerId
        );

        assertEquals("cs_email", response.path("id").asText());
        harness.server().verify();
    }

    @Test
    void createCheckoutSessionBuildsFrontendSuccessAndCancelUrls() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.25"));

        harness.server()
                .expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(formContains(Map.of(
                        "success_url", "http://localhost:5173/subscription/success?session_id={CHECKOUT_SESSION_ID}",
                        "cancel_url", "http://localhost:5173/subscription/cancel"
                )))
                .andRespond(withSuccess("{\"id\":\"cs_urls\"}", MediaType.APPLICATION_JSON));

        harness.service().createCheckoutSession(
                UUID.randomUUID(),
                "reader@example.com",
                plan,
                null
        );

        harness.server().verify();
    }

    @Test
    void createCheckoutSessionUsesFreshIdempotencyKeyForEveryAttempt() {
        Harness harness = harness();
        SubscriptionPlanEntity plan = plan("USD", new BigDecimal("10.25"));
        UUID userId = UUID.randomUUID();
        List<String> keys = new ArrayList<>();

        harness.server()
                .expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(captureHeader("Idempotency-Key", keys))
                .andRespond(withSuccess("{\"id\":\"cs_1\"}", MediaType.APPLICATION_JSON));
        harness.server()
                .expect(requestTo(API + "/v1/checkout/sessions"))
                .andExpect(captureHeader("Idempotency-Key", keys))
                .andRespond(withSuccess("{\"id\":\"cs_2\"}", MediaType.APPLICATION_JSON));

        harness.service().createCheckoutSession(userId, "reader@example.com", plan, null);
        harness.service().createCheckoutSession(userId, "reader@example.com", plan, null);

        assertEquals(2, keys.size());
        assertNotEquals(keys.get(0), keys.get(1));
        assertTrue(keys.get(0).startsWith("checkout-" + userId + "-"));
        assertTrue(keys.get(1).startsWith("checkout-" + userId + "-"));
        harness.server().verify();
    }

    @Test
    void retrieveCheckoutSessionPerformsAuthenticatedGet() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/checkout/sessions/cs_123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andRespond(withSuccess("{\"id\":\"cs_123\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().retrieveCheckoutSession("cs_123");

        assertEquals("cs_123", response.path("id").asText());
        harness.server().verify();
    }

    @Test
    void retrieveCheckoutSessionMapsStripeApiFailure() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/checkout/sessions/cs_missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withStatus(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":{\"message\":\"Checkout session not found\"}}")
                );

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().retrieveCheckoutSession("cs_missing")
        );

        assertEquals(502, error.getCode());
        assertEquals("Checkout session not found", error.getMessage());
        harness.server().verify();
    }

    @Test
    void retrieveSubscriptionPerformsAuthenticatedGet() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/subscriptions/sub_123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andRespond(withSuccess("{\"id\":\"sub_123\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().retrieveSubscription("sub_123");

        assertEquals("sub_123", response.path("id").asText());
        harness.server().verify();
    }

    @Test
    void retrieveSubscriptionMapsStripeApiFailure() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/subscriptions/sub_missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":{\"message\":\"Subscription is invalid\"}}")
                );

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().retrieveSubscription("sub_missing")
        );

        assertEquals(502, error.getCode());
        assertEquals("Subscription is invalid", error.getMessage());
        harness.server().verify();
    }

    @Test
    void createBillingPortalSessionBuildsCustomerAndReturnUrlWithoutIdempotencyKey() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/billing_portal/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of(
                        "customer", "cus_123",
                        "return_url", "http://localhost:5173/profile"
                )))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess("{\"id\":\"bps_123\",\"url\":\"https://billing\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createBillingPortalSession("cus_123");

        assertEquals("bps_123", response.path("id").asText());
        harness.server().verify();
    }

    // ===== payout connected account =====

    @Test
    void createPayoutConnectedAccountRejectsMissingUserId() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createPayoutConnectedAccount(
                        null,
                        "a@b.com",
                        "VN",
                        "AUTHOR",
                        "USD"
                )
        );

        assertEquals(400, error.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VNM", "1", "V-", "USA1"})
    void createPayoutConnectedAccountRejectsInvalidCountry(String country) {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createPayoutConnectedAccount(
                        UUID.randomUUID(),
                        "a@b.com",
                        country,
                        "AUTHOR",
                        "USD"
                )
        );

        assertEquals(400, error.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void createPayoutConnectedAccountRejectsBlankCountry(String country) {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createPayoutConnectedAccount(
                        UUID.randomUUID(),
                        "a@b.com",
                        country,
                        "AUTHOR",
                        "USD"
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void createPayoutConnectedAccountRejectsUnsupportedCurrency() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createPayoutConnectedAccount(
                        UUID.randomUUID(),
                        "a@b.com",
                        "VN",
                        "AUTHOR",
                        "ABC"
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void createPayoutConnectedAccountDefaultsMissingCountryToRecipientVietnam() {
        Harness harness = harness();
        UUID userId = UUID.randomUUID();

        harness.server()
                .expect(requestTo(API + "/v1/accounts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.ofEntries(
                        Map.entry("country", "VN"),
                        Map.entry("default_currency", "usd"),
                        Map.entry("tos_acceptance[service_agreement]", "recipient"),
                        Map.entry("capabilities[transfers][requested]", "true"),
                        Map.entry("controller[stripe_dashboard][type]", "express"),
                        Map.entry("metadata[user_id]", userId.toString()),
                        Map.entry("metadata[creator_role]", "AUTHOR"),
                        Map.entry("metadata[service_agreement]", "recipient"),
                        Map.entry("metadata[payout_currency]", "USD")
                )))
                .andExpect(formLacks("capabilities[card_payments][requested]"))
                .andExpect(headerMatchesPrefix(
                        "Idempotency-Key",
                        "creator-connect-account-v3-" + userId + "-VN-USD-recipient"
                ))
                .andRespond(withSuccess("{\"id\":\"acct_new\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createPayoutConnectedAccount(
                userId,
                "  author@example.com  ",
                null,
                "AUTHOR",
                "USD"
        );

        assertEquals("acct_new", response.path("id").asText());
        harness.server().verify();
    }

    @Test
    void createPayoutConnectedAccountUsesFullAgreementForNonRecipientCountry() {
        Harness harness = harness();
        UUID userId = UUID.randomUUID();

        harness.server()
                .expect(requestTo(API + "/v1/accounts"))
                .andExpect(formContains(Map.of(
                        "country", "FR",
                        "tos_acceptance[service_agreement]", "full",
                        "metadata[service_agreement]", "full"
                )))
                .andRespond(withSuccess("{\"id\":\"acct_fr\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createPayoutConnectedAccount(
                userId,
                "author@example.com",
                "fr",
                null,
                "EUR"
        );

        assertEquals("acct_fr", response.path("id").asText());
        harness.server().verify();
    }

    @Test
    void createPayoutConnectedAccountTrimsOptionalEmail() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/accounts"))
                .andExpect(formContains(Map.of("email", "author@example.com")))
                .andRespond(withSuccess("{\"id\":\"acct_email\"}", MediaType.APPLICATION_JSON));

        harness.service().createPayoutConnectedAccount(
                UUID.randomUUID(),
                "  author@example.com  ",
                "VN",
                "AUTHOR",
                "USD"
        );

        harness.server().verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void createPayoutConnectedAccountOmitsMissingOrBlankEmail(String email) {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/accounts"))
                .andExpect(formLacks("email"))
                .andRespond(withSuccess("{\"id\":\"acct_no_email\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createPayoutConnectedAccount(
                UUID.randomUUID(),
                email,
                "VN",
                "AUTHOR",
                "USD"
        );

        assertEquals("acct_no_email", response.path("id").asText());
        harness.server().verify();
    }

    // ===== onboarding link =====

    @Test
    void createPayoutAccountOnboardingLinkRejectsMalformedAccountId() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createPayoutAccountOnboardingLink(
                        "bad",
                        "/refresh",
                        "/return"
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void createPayoutAccountOnboardingLinkNormalizesFrontendPathsAndUsesNoIdempotencyKey() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/account_links"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of(
                        "account", "acct_123",
                        "refresh_url", "http://localhost:5173/refresh",
                        "return_url", "http://localhost:5173/return",
                        "type", "account_onboarding",
                        "collection_options[fields]", "eventually_due",
                        "collection_options[future_requirements]", "include"
                )))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess("{\"url\":\"https://connect.stripe.test\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createPayoutAccountOnboardingLink(
                "acct_123",
                "refresh",
                "/return"
        );

        assertEquals(
                "https://connect.stripe.test",
                response.path("url").asText()
        );
        harness.server().verify();
    }

    @Test
    void createPayoutAccountOnboardingLinkDefaultsBlankPathsToFrontendRoot() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/account_links"))
                .andExpect(formContains(Map.of(
                        "refresh_url", "http://localhost:5173/",
                        "return_url", "http://localhost:5173/"
                )))
                .andRespond(withSuccess("{\"url\":\"https://connect.stripe.test/root\"}", MediaType.APPLICATION_JSON));

        harness.service().createPayoutAccountOnboardingLink(
                "acct_123",
                null,
                "   "
        );

        harness.server().verify();
    }

    // ===== connected account currency / retrieval =====

    @Test
    void updateConnectedAccountDefaultCurrencyRejectsMalformedAccountId() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateConnectedAccountDefaultCurrency("bad", "USD")
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void updateConnectedAccountDefaultCurrencyRejectsUnsupportedCurrency() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.updateConnectedAccountDefaultCurrency("acct_123", "ABC")
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void updateConnectedAccountDefaultCurrencyBuildsExpectedStripeRequest() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/accounts/acct_123"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of(
                        "default_currency", "eur",
                        "metadata[payout_currency]", "EUR"
                )))
                .andExpect(header(
                        "Idempotency-Key",
                        "creator-connect-currency-acct_123-EUR"
                ))
                .andRespond(withSuccess("{\"id\":\"acct_123\",\"default_currency\":\"eur\"}", MediaType.APPLICATION_JSON));

        JsonNode response =
                harness.service().updateConnectedAccountDefaultCurrency("acct_123", "eur");

        assertEquals("acct_123", response.path("id").asText());
        harness.server().verify();
    }

    @Test
    void retrieveConnectedAccountRejectsMalformedAccountId() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.retrieveConnectedAccount("bad")
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void retrieveConnectedAccountPerformsAuthenticatedGet() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/accounts/acct_123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andRespond(withSuccess("{\"id\":\"acct_123\",\"payouts_enabled\":true}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().retrieveConnectedAccount("acct_123");

        assertEquals("acct_123", response.path("id").asText());
        harness.server().verify();
    }

    // ===== transfer =====

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void createTransferRejectsNonPositiveAmount(String amount) {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createTransfer(
                        "acct_123",
                        new BigDecimal(amount),
                        "USD",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "2026-07"
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void createTransferRejectsNullAmount() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createTransfer(
                        "acct_123",
                        null,
                        "USD",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "2026-07"
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void createTransferAcceptsOneCentPositiveAmount() {
        Harness harness = harness();
        UUID payoutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        harness.server()
                .expect(requestTo(API + "/v1/transfers"))
                .andExpect(formContains(Map.of(
                        "amount", "1",
                        "currency", "usd"
                )))
                .andRespond(withSuccess("{\"id\":\"tr_one_cent\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createTransfer(
                "acct_123",
                new BigDecimal("0.01"),
                "USD",
                payoutId,
                userId,
                "2026-07"
        );

        assertEquals("tr_one_cent", response.path("id").asText());
        harness.server().verify();
    }

    @Test
    void createTransferRejectsUnsupportedCurrency() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.createTransfer(
                        "acct_123",
                        BigDecimal.ONE,
                        "BTC",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "2026-07"
                )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void createTransferBuildsStripeTransferRequestAndStableIdempotencyKey() {
        Harness harness = harness();
        UUID payoutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        harness.server()
                .expect(requestTo(API + "/v1/transfers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andExpect(header(
                        "Idempotency-Key",
                        "creator-payout-" + payoutId
                ))
                .andExpect(formContains(Map.ofEntries(
                        Map.entry("amount", "2500"),
                        Map.entry("currency", "usd"),
                        Map.entry("destination", "acct_123"),
                        Map.entry("description", "ComiVerse payout 2026-07"),
                        Map.entry("transfer_group", "COMIVERSE_PAYOUT_" + payoutId),
                        Map.entry("metadata[payout_request_id]", payoutId.toString()),
                        Map.entry("metadata[user_id]", userId.toString()),
                        Map.entry("metadata[payout_month]", "2026-07")
                )))
                .andRespond(withSuccess("{\"id\":\"tr_123\"}", MediaType.APPLICATION_JSON));

        JsonNode response = harness.service().createTransfer(
                "acct_123",
                new BigDecimal("25.00"),
                "USD",
                payoutId,
                userId,
                "2026-07"
        );

        assertEquals("tr_123", response.path("id").asText());
        harness.server().verify();
    }

    // ===== webhook verification =====

    @Test
    void verifyWebhookAcceptsValidSignatureAndParsesPayload() throws Exception {
        String payload = "{\"id\":\"evt_123\",\"type\":\"checkout.session.completed\"}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = hmac(WEBHOOK_SECRET, timestamp + "." + payload);

        JsonNode node = serviceOnly(TEST_KEY, WEBHOOK_SECRET).verifyAndParseWebhook(
                payload,
                "t=" + timestamp + ",v1=" + signature
        );

        assertEquals("evt_123", node.path("id").asText());
    }

    @Test
    void verifyWebhookRejectsMissingWebhookSecret() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, "")
                        .verifyAndParseWebhook("{}", "t=1,v1=aa")
        );

        assertEquals(503, error.getCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void verifyWebhookRejectsMissingSignatureHeader(String header) {
        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook("{}", header)
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void verifyWebhookRejectsInvalidTimestampText() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook("{}", "t=not-a-number,v1=aa")
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("timestamp"));
    }

    @Test
    void verifyWebhookRejectsHeaderWithoutTimestamp() {
        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook("{}", "v1=aa")
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void verifyWebhookRejectsHeaderWithoutV1Signature() {
        long now = Instant.now().getEpochSecond();

        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook("{}", "t=" + now)
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void verifyWebhookAcceptsTimestampInsideTolerance() throws Exception {
        String payload = "{\"id\":\"evt_inside\"}";
        long timestamp = Instant.now().minusSeconds(299).getEpochSecond();
        String signature = hmac(WEBHOOK_SECRET, timestamp + "." + payload);

        JsonNode node = serviceOnly(TEST_KEY, WEBHOOK_SECRET).verifyAndParseWebhook(
                payload,
                "t=" + timestamp + ",v1=" + signature
        );

        assertEquals("evt_inside", node.path("id").asText());
    }

    @Test
    void verifyWebhookRejectsTimestampOutsideTolerance() {
        long timestamp = Instant.now().minusSeconds(301).getEpochSecond();

        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook(
                                "{}",
                                "t=" + timestamp + ",v1=00"
                        )
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("tolerance"));
    }

    @Test
    void verifyWebhookRejectsTimestampTooFarInFuture() {
        long timestamp = Instant.now().plusSeconds(301).getEpochSecond();

        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook(
                                "{}",
                                "t=" + timestamp + ",v1=00"
                        )
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("tolerance"));
    }

    @Test
    void verifyWebhookRejectsWrongSignature() {
        long now = Instant.now().getEpochSecond();

        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook("{}", "t=" + now + ",v1=00")
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("verification failed"));
    }

    @Test
    void verifyWebhookRejectsMalformedHexSignature() {
        long now = Instant.now().getEpochSecond();

        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook(
                                "{}",
                                "t=" + now + ",v1=not-hex"
                        )
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void verifyWebhookAcceptsWhenAnyV1SignatureMatches() throws Exception {
        String payload = "{\"id\":\"evt_multi\"}";
        long timestamp = Instant.now().getEpochSecond();
        String valid = hmac(WEBHOOK_SECRET, timestamp + "." + payload);

        JsonNode node = serviceOnly(TEST_KEY, WEBHOOK_SECRET).verifyAndParseWebhook(
                payload,
                "t=" + timestamp + ",v1=00,v1=" + valid
        );

        assertEquals("evt_multi", node.path("id").asText());
    }

    @Test
    void verifyWebhookRejectsMalformedJsonAfterValidSignature() throws Exception {
        String payload = "{not-json";
        long timestamp = Instant.now().getEpochSecond();
        String valid = hmac(WEBHOOK_SECRET, timestamp + "." + payload);

        CustomException error = assertThrows(
                CustomException.class,
                () -> serviceOnly(TEST_KEY, WEBHOOK_SECRET)
                        .verifyAndParseWebhook(
                                payload,
                                "t=" + timestamp + ",v1=" + valid
                        )
        );

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("payload"));
    }

    // ===== transfers capability =====

    @Test
    void requestTransfersCapabilityRejectsMalformedAccountId() {
        StripeGatewayService service = serviceOnly(TEST_KEY, WEBHOOK_SECRET);

        CustomException error = assertThrows(
                CustomException.class,
                () -> service.requestTransfersCapability("bad")
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void requestTransfersCapabilityBuildsExpectedStripeRequest() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/accounts/acct_123/capabilities/transfers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(formContains(Map.of("requested", "true")))
                .andExpect(header(
                        "Idempotency-Key",
                        "request-transfers-acct_123"
                ))
                .andRespond(withSuccess("{\"id\":\"transfers\",\"status\":\"active\"}", MediaType.APPLICATION_JSON));

        JsonNode response =
                harness.service().requestTransfersCapability("acct_123");

        assertEquals("active", response.path("status").asText());
        harness.server().verify();
    }

    // ===== Stripe API error mapping =====

    @Test
    void stripeApiErrorUsesStripeJsonMessage() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/billing_portal/sessions"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":{\"message\":\"Customer does not exist\"}}")
                );

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createBillingPortalSession("cus_missing")
        );

        assertEquals(502, error.getCode());
        assertEquals("Customer does not exist", error.getMessage());
        harness.server().verify();
    }

    @Test
    void stripeApiErrorFallsBackWhenJsonDoesNotContainErrorMessage() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/billing_portal/sessions"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":{\"type\":\"invalid_request_error\"}}")
                );

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createBillingPortalSession("cus_bad")
        );

        assertEquals(502, error.getCode());
        assertEquals("Stripe request failed", error.getMessage());
        harness.server().verify();
    }

    @Test
    void stripeApiErrorFallsBackWhenResponseBodyIsNotJson() {
        Harness harness = harness();

        harness.server()
                .expect(requestTo(API + "/v1/billing_portal/sessions"))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.TEXT_PLAIN)
                                .body("not-json")
                );

        CustomException error = assertThrows(
                CustomException.class,
                () -> harness.service().createBillingPortalSession("cus_bad")
        );

        assertEquals(502, error.getCode());
        assertTrue(error.getMessage().contains("Stripe request failed"));
        harness.server().verify();
    }

    // ===== helpers =====

    private Harness harness() {
        return harness(TEST_KEY, WEBHOOK_SECRET);
    }

    private Harness harness(String key, String webhookSecret) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        StripeGatewayService service = new StripeGatewayService(
                builder,
                mapper,
                key,
                webhookSecret,
                "http://localhost:5173/",
                "VN,US"
        );

        return new Harness(service, server);
    }

    private StripeGatewayService serviceOnly(String key, String webhookSecret) {
        return new StripeGatewayService(
                RestClient.builder(),
                mapper,
                key,
                webhookSecret,
                "http://localhost:5173/",
                "VN,US"
        );
    }

    private SubscriptionPlanEntity plan(String currency, BigDecimal price) {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
                .code("PREMIUM")
                .name("Premium")
                .description("Premium monthly plan")
                .price(price)
                .currency(currency)
                .billingInterval(BillingInterval.MONTH)
                .intervalCount(1)
                .stripeProductId("prod_123")
                .stripePriceId("price_123")
                .build();
        plan.setId(UUID.randomUUID());
        return plan;
    }

    private RequestMatcher formContains(Map<String, String> expected) {
        return request -> {
            Map<String, List<String>> actual = parseFormBody(request);
            expected.forEach((key, value) -> {
                assertTrue(actual.containsKey(key), "Missing form field: " + key);
                assertTrue(
                        actual.get(key).contains(value),
                        "Expected form field " + key + "=" + value
                                + " but was " + actual.get(key)
                );
            });
        };
    }

    private RequestMatcher formLacks(String key) {
        return request -> assertFalse(
                parseFormBody(request).containsKey(key),
                "Unexpected form field: " + key
        );
    }

    private Map<String, List<String>> parseFormBody(
            org.springframework.http.client.ClientHttpRequest request
    ) {
        assertInstanceOf(MockClientHttpRequest.class, request);

        String body = ((MockClientHttpRequest) request).getBodyAsString();
        Map<String, List<String>> form = new LinkedHashMap<>();

        if (body == null || body.isBlank()) {
            return form;
        }

        for (String token : body.split("&")) {
            String[] pair = token.split("=", 2);
            String key = decode(pair[0]);
            String value = pair.length == 2 ? decode(pair[1]) : "";
            form.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return form;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private RequestMatcher captureHeader(String name, List<String> target) {
        return request -> {
            String value = request.getHeaders().getFirst(name);
            assertNotNull(value, "Missing header: " + name);
            target.add(value);
        };
    }

    private RequestMatcher headerMatchesPrefix(String name, String prefix) {
        return request -> {
            String value = request.getHeaders().getFirst(name);
            assertNotNull(value, "Missing header: " + name);
            assertTrue(
                    value.startsWith(prefix),
                    "Expected " + name + " to start with " + prefix + " but was " + value
            );
        };
    }

    private String hmac(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        return HexFormat.of().formatHex(
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
        );
    }

    private record Harness(
            StripeGatewayService service,
            MockRestServiceServer server
    ) {}
}
