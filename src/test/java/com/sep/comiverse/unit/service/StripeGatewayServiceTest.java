package com.sep.comiverse.unit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.service.StripeGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StripeGatewayServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void isConfigured_acceptsOnlySandboxSecretKey() {
        assertTrue(service("sk_test_123", "whsec_123").isConfigured());
        assertFalse(service("", "whsec_123").isConfigured());
        assertFalse(service("sk_live_123", "whsec_123").isConfigured());
    }

    @Test
    void payoutAccountValidation_rejectsNullUserInvalidCountryAndCurrency() {
        StripeGatewayService service = service("sk_test_123", "whsec_123");

        assertEquals(400, assertThrows(CustomException.class, () ->
                service.createPayoutConnectedAccount(null, "a@b.com", "VN", "AUTHOR", "USD"))
                .getCode());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.createPayoutConnectedAccount(UUID.randomUUID(), "a@b.com", "VNM", "AUTHOR", "USD"))
                .getCode());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.createPayoutConnectedAccount(UUID.randomUUID(), "a@b.com", "VN", "AUTHOR", "ABC"))
                .getCode());
    }

    @Test
    void onboardingAndAccountValidation_rejectMalformedStripeAccountIdsBeforeNetworkCall() {
        StripeGatewayService service = service("sk_test_123", "whsec_123");

        assertEquals(400, assertThrows(CustomException.class, () ->
                service.createPayoutAccountOnboardingLink("bad", "/refresh", "/return"))
                .getCode());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.updateConnectedAccountDefaultCurrency("bad", "USD"))
                .getCode());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.retrieveConnectedAccount("bad"))
                .getCode());
    }

    @Test
    void createTransfer_rejectsNonPositiveAmountAndUnknownCurrency() {
        StripeGatewayService service = service("sk_test_123", "whsec_123");
        UUID payoutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertEquals(400, assertThrows(CustomException.class, () ->
                service.createTransfer("acct_123", BigDecimal.ZERO, "USD", payoutId, userId, "2026-07"))
                .getCode());
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.createTransfer("acct_123", BigDecimal.ONE, "BTC", payoutId, userId, "2026-07"))
                .getCode());
    }

    @Test
    void verifyWebhook_acceptsValidSignatureAndParsesPayload() throws Exception {
        String secret = "whsec_unit_test";
        StripeGatewayService service = service("sk_test_123", secret);
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"id\":\"evt_123\",\"type\":\"checkout.session.completed\"}";
        String signature = hmac(secret, timestamp + "." + payload);

        JsonNode node = service.verifyAndParseWebhook(
                payload,
                "t=" + timestamp + ",v1=" + signature
        );

        assertEquals("evt_123", node.path("id").asText());
    }

    @Test
    void verifyWebhook_rejectsMissingSecretHeaderOldTimestampAndWrongSignature() {
        assertEquals(503, assertThrows(CustomException.class, () ->
                service("sk_test_123", "").verifyAndParseWebhook("{}", "t=1,v1=aa"))
                .getCode());

        StripeGatewayService service = service("sk_test_123", "whsec_x");
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.verifyAndParseWebhook("{}", null)).getCode());

        long old = Instant.now().minusSeconds(600).getEpochSecond();
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.verifyAndParseWebhook("{}", "t=" + old + ",v1=aa")).getCode());

        long now = Instant.now().getEpochSecond();
        assertEquals(400, assertThrows(CustomException.class, () ->
                service.verifyAndParseWebhook("{}", "t=" + now + ",v1=00")).getCode());
    }

    @Test
    void minorUnitConversion_handlesZeroDecimalAndTwoDecimalCurrencies() {
        StripeGatewayService service = service("sk_test_123", "whsec_x");

        Long vnd = ReflectionTestUtils.invokeMethod(service, "toMinorUnit", new BigDecimal("79000"), "VND");
        Long usd = ReflectionTestUtils.invokeMethod(service, "toMinorUnit", new BigDecimal("10.25"), "USD");
        assertEquals(79000L, vnd);
        assertEquals(1025L, usd);

        assertThrows(CustomException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "toMinorUnit", new BigDecimal("10.001"), "USD"));
    }

    private StripeGatewayService service(String key, String webhookSecret) {
        return new StripeGatewayService(
                RestClient.builder(), mapper, key, webhookSecret,
                "http://localhost:5173/", "VN,US"
        );
    }

    private String hmac(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
