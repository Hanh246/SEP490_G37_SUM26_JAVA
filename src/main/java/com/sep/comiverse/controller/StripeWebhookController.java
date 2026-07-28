package com.sep.comiverse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep.comiverse.service.StripeGatewayService;
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

        // Gọi service xử lý event thật tại đây
        // stripeWebhookService.processEvent(event);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Stripe webhook received"
        ));
    }
}