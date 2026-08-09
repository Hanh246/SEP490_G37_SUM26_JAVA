package com.sep.comiverse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep.comiverse.service.StripeGatewayService;
import com.sep.comiverse.service.CreatorPayoutAccountService;
import com.sep.comiverse.service.StripeSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeGatewayService stripeGatewayService;
    private final StripeSubscriptionService stripeSubscriptionService;
    private final CreatorPayoutAccountService payoutProfileService;

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(
                    name = "Stripe-Signature",
                    required = false
            ) String stripeSignature
    ) {
        String payload = new String(rawBody, StandardCharsets.UTF_8);

        log.info(
                "Stripe webhook received: payloadBytes={}, signaturePresent={}",
                rawBody.length,
                stripeSignature != null && !stripeSignature.isBlank()
        );

        JsonNode event = stripeGatewayService.verifyAndParseWebhook(
                payload,
                stripeSignature
        );

        log.info(
                "Stripe webhook verified: eventId={}, eventType={}",
                event.path("id").asText(),
                event.path("type").asText()
        );

        if ("account.updated".equals(event.path("type").asText())) {
            payoutProfileService.syncFromAccountUpdatedWebhook(
                    event.path("data").path("object")
            );
        }

        stripeSubscriptionService.processWebhook(event);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Stripe webhook received"
        ));
    }
}
