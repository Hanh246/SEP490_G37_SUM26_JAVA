package com.sep.comiverse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.entity.SubscriptionPlanEntity;
import com.sep.comiverse.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class StripeGatewayService {
    private static final String STRIPE_API_BASE_URL = "https://api.stripe.com";
    private static final long WEBHOOK_TOLERANCE_SECONDS = 300L;
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final String webhookSecret;
    private final String frontendUrl;

    public StripeGatewayService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${frontend.url:http://localhost:5173}") String frontendUrl
    ) {
        this.objectMapper = objectMapper;
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.frontendUrl = trimTrailingSlash(frontendUrl);
        this.restClient = restClientBuilder.baseUrl(STRIPE_API_BASE_URL).build();
    }

    public boolean isConfigured() {
        return !secretKey.isBlank() && secretKey.startsWith("sk_test_");
    }

    public String createProduct(SubscriptionPlanEntity plan) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", plan.getName());
        if (plan.getDescription() != null && !plan.getDescription().isBlank()) {
            form.add("description", plan.getDescription());
        }
        form.add("metadata[plan_id]", plan.getId().toString());
        form.add("metadata[plan_code]", plan.getCode());
        JsonNode response = postForm(
                "/v1/products",
                form,
                catalogIdempotencyKey("product", plan)
        );
        return requiredText(response, "id", "Stripe did not return a product ID");
    }

    public void updateProduct(SubscriptionPlanEntity plan) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", plan.getName());
        form.add("description", plan.getDescription() == null ? "" : plan.getDescription());
        form.add("metadata[plan_id]", plan.getId().toString());
        form.add("metadata[plan_code]", plan.getCode());
        postForm(
                "/v1/products/" + plan.getStripeProductId(),
                form,
                catalogIdempotencyKey("product-update", plan)
        );
    }

    public String createRecurringPrice(SubscriptionPlanEntity plan, String productId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("product", productId);
        form.add("currency", plan.getCurrency().toLowerCase(Locale.ROOT));
        form.add("unit_amount", toMinorUnit(plan.getPrice(), plan.getCurrency()).toString());
        form.add("recurring[interval]", plan.getBillingInterval().name().toLowerCase(Locale.ROOT));
        form.add("recurring[interval_count]", String.valueOf(plan.getIntervalCount()));
        form.add("nickname", plan.getName());
        form.add("metadata[plan_id]", plan.getId().toString());
        form.add("metadata[plan_code]", plan.getCode());
        JsonNode response = postForm(
                "/v1/prices",
                form,
                catalogIdempotencyKey("price", plan)
        );
        return requiredText(response, "id", "Stripe did not return a price ID");
    }

    public JsonNode createCheckoutSession(
            UUID userId,
            String userEmail,
            SubscriptionPlanEntity plan,
            String existingCustomerId
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("success_url", frontendUrl + "/subscription/success?session_id={CHECKOUT_SESSION_ID}");
        form.add("cancel_url", frontendUrl + "/subscription/cancel");
        form.add("client_reference_id", userId.toString());
        if (existingCustomerId != null && !existingCustomerId.isBlank()) {
            form.add("customer", existingCustomerId);
        } else {
            form.add("customer_email", userEmail);
        }
        form.add("line_items[0][price]", plan.getStripePriceId());
        form.add("line_items[0][quantity]", "1");
        form.add("metadata[user_id]", userId.toString());
        form.add("metadata[plan_id]", plan.getId().toString());
        form.add("metadata[plan_code]", plan.getCode());
        form.add("subscription_data[metadata][user_id]", userId.toString());
        form.add("subscription_data[metadata][plan_id]", plan.getId().toString());
        form.add("subscription_data[metadata][plan_code]", plan.getCode());
        form.add("billing_address_collection", "auto");
        form.add("payment_method_collection", "always");
        // Every explicit checkout attempt gets a fresh Stripe session. Reusing a time-bucketed
        // idempotency key can return a previously cancelled or expired Checkout URL.
        return postForm(
                "/v1/checkout/sessions",
                form,
                "checkout-" + userId + "-" + UUID.randomUUID()
        );
    }

    public JsonNode retrieveCheckoutSession(String sessionId) {
        requireSecretKey();
        try {
            return restClient.get()
                    .uri("/v1/checkout/sessions/{id}", sessionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw stripeException(ex, "Unable to retrieve Stripe Checkout session");
        }
    }

    public JsonNode retrieveSubscription(String subscriptionId) {
        requireSecretKey();
        try {
            return restClient.get()
                    .uri("/v1/subscriptions/{id}", subscriptionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw stripeException(ex, "Unable to retrieve Stripe subscription");
        }
    }

    public JsonNode createBillingPortalSession(String customerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerId);
        form.add("return_url", frontendUrl + "/profile");
        return postForm("/v1/billing_portal/sessions", form, null);
    }

    public JsonNode verifyAndParseWebhook(String payload, String signatureHeader) {
        if (webhookSecret.isBlank()) {
            throw new CustomException(503, "Stripe webhook secret is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new CustomException(400, "Missing Stripe-Signature header", HttpStatus.BAD_REQUEST);
        }

        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("t".equals(pair[0])) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException ignored) {
                    throw new CustomException(400, "Invalid Stripe webhook timestamp", HttpStatus.BAD_REQUEST);
                }
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }

        if (timestamp == null || signatures.isEmpty()) {
            throw new CustomException(400, "Invalid Stripe webhook signature", HttpStatus.BAD_REQUEST);
        }

        long age = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (age > WEBHOOK_TOLERANCE_SECONDS) {
            throw new CustomException(400, "Stripe webhook timestamp is outside the allowed tolerance", HttpStatus.BAD_REQUEST);
        }

        String signedPayload = timestamp + "." + payload;
        byte[] expected = hmacSha256(webhookSecret, signedPayload);
        boolean verified = signatures.stream().anyMatch(signature -> {
            try {
                byte[] supplied = HexFormat.of().parseHex(signature);
                return MessageDigest.isEqual(expected, supplied);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        });

        if (!verified) {
            throw new CustomException(400, "Stripe webhook signature verification failed", HttpStatus.BAD_REQUEST);
        }

        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new CustomException(400, "Invalid Stripe webhook payload", HttpStatus.BAD_REQUEST);
        }
    }

    private JsonNode postForm(String path, MultiValueMap<String, String> form) {
        return postForm(path, form, null);
    }

    private JsonNode postForm(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        requireSecretKey();
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                request.header("Idempotency-Key", idempotencyKey);
            }
            return request
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw stripeException(ex, "Stripe request failed");
        }
    }

    private String catalogIdempotencyKey(String operation, SubscriptionPlanEntity plan) {
        String fingerprint = String.join(
                "|",
                operation,
                plan.getId().toString(),
                plan.getCode(),
                plan.getName(),
                String.valueOf(plan.getDescription()),
                plan.getPrice().toPlainString(),
                plan.getCurrency(),
                plan.getBillingInterval().name(),
                plan.getIntervalCount().toString()
        );
        return operation + "-" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private void requireSecretKey() {
        if (secretKey.isBlank()) {
            throw new CustomException(503, "Stripe sandbox secret key is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (!secretKey.startsWith("sk_test_")) {
            throw new CustomException(503, "Only a Stripe sandbox key (sk_test_...) is allowed in this configuration", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private CustomException stripeException(RestClientResponseException ex, String fallback) {
        String message = fallback;
        try {
            JsonNode root = objectMapper.readTree(ex.getResponseBodyAsString());
            String stripeMessage = root.path("error").path("message").asText();
            if (!stripeMessage.isBlank()) message = stripeMessage;
        } catch (Exception ignored) {
            if (ex.getStatusText() != null && !ex.getStatusText().isBlank()) {
                message = fallback + ": " + ex.getStatusText();
            }
        }
        log.warn("Stripe API error status={} message={}", ex.getStatusCode(), message);
        return new CustomException(502, message, HttpStatus.BAD_GATEWAY);
    }

    private Long toMinorUnit(BigDecimal amount, String currency) {
        String normalizedCurrency = currency.toUpperCase(Locale.ROOT);
        BigDecimal converted = ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)
                ? amount
                : amount.multiply(BigDecimal.valueOf(100));
        try {
            return converted.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new CustomException(400, "Price has too many decimal places for " + normalizedCurrency, HttpStatus.BAD_REQUEST);
        }
    }

    private byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new CustomException(500, "Unable to verify Stripe webhook", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = node == null ? "" : node.path(field).asText();
        if (value.isBlank()) {
            throw new CustomException(502, message, HttpStatus.BAD_GATEWAY);
        }
        return value;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://localhost:5173";
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }
}
