package com.sep.comiverse.unit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.controller.StripeWebhookController;
import com.sep.comiverse.exception.CustomException;
import com.sep.comiverse.service.CreatorPayoutAccountService;
import com.sep.comiverse.service.StripeGatewayService;
import com.sep.comiverse.service.StripeSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StripeGatewayService stripeGatewayService;

    @Mock
    private StripeSubscriptionService stripeSubscriptionService;

    @Mock
    private CreatorPayoutAccountService payoutProfileService;

    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(
                stripeGatewayService,
                stripeSubscriptionService,
                payoutProfileService
        );
    }

    @Test
    void verifiedWebhookIsProcessedAndAcknowledged() throws Exception {
        // Arrange
        byte[] rawBody = """
                {"id":"evt_123","type":"invoice.paid","data":{"object":{}}}
                """.getBytes(StandardCharsets.UTF_8);
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode event = objectMapper.readTree(payload);

        when(stripeGatewayService.verifyAndParseWebhook(payload, "signature"))
                .thenReturn(event);

        // Act
        ResponseEntity<?> response = controller.handleWebhook(rawBody, "signature");

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertEquals(
                Map.of(
                        "success", true,
                        "message", "Stripe webhook received"
                ),
                response.getBody()
        );

        verify(stripeGatewayService).verifyAndParseWebhook(payload, "signature");
        verify(stripeSubscriptionService).processWebhook(event);
        verifyNoInteractions(payoutProfileService);
    }

    @Test
    void accountUpdatedSyncsPayoutProfileBeforeSubscriptionProcessing() throws Exception {
        // Arrange
        byte[] rawBody = """
                {
                  "id":"evt_account_123",
                  "type":"account.updated",
                  "data":{
                    "object":{
                      "id":"acct_123",
                      "details_submitted":true,
                      "payouts_enabled":true
                    }
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode event = objectMapper.readTree(payload);
        JsonNode stripeAccount = event.path("data").path("object");

        when(stripeGatewayService.verifyAndParseWebhook(payload, "stripe-signature"))
                .thenReturn(event);

        // Act
        ResponseEntity<?> response = controller.handleWebhook(
                rawBody,
                "stripe-signature"
        );

        // Assert
        assertEquals(200, response.getStatusCode().value());

        InOrder inOrder = inOrder(
                stripeGatewayService,
                payoutProfileService,
                stripeSubscriptionService
        );
        inOrder.verify(stripeGatewayService)
                .verifyAndParseWebhook(payload, "stripe-signature");
        inOrder.verify(payoutProfileService)
                .syncFromAccountUpdatedWebhook(stripeAccount);
        inOrder.verify(stripeSubscriptionService)
                .processWebhook(event);
    }

    @Test
    void missingSignatureStopsWebhookProcessing() {
        // Arrange
        byte[] rawBody = """
                {"id":"evt_no_sig","type":"invoice.paid","data":{"object":{}}}
                """.getBytes(StandardCharsets.UTF_8);
        String payload = new String(rawBody, StandardCharsets.UTF_8);

        CustomException missingSignatureError = new CustomException(
                400,
                "Missing Stripe-Signature header",
                HttpStatus.BAD_REQUEST
        );
        when(stripeGatewayService.verifyAndParseWebhook(payload, null))
                .thenThrow(missingSignatureError);

        // Act + Assert
        CustomException exception = assertThrows(
                CustomException.class,
                () -> controller.handleWebhook(rawBody, null)
        );

        assertEquals(400, exception.getCode());
        assertEquals("Missing Stripe-Signature header", exception.getMessage());
        verify(stripeGatewayService).verifyAndParseWebhook(payload, null);
        verifyNoInteractions(stripeSubscriptionService, payoutProfileService);
    }

    @Test
    void verificationFailureStopsAllDownstreamWebhookProcessing() {
        // Arrange
        byte[] rawBody = """
                {"id":"evt_bad_sig","type":"invoice.paid","data":{"object":{}}}
                """.getBytes(StandardCharsets.UTF_8);
        String payload = new String(rawBody, StandardCharsets.UTF_8);

        CustomException verificationError = new CustomException(
                400,
                "Stripe webhook signature verification failed",
                HttpStatus.BAD_REQUEST
        );
        when(stripeGatewayService.verifyAndParseWebhook(payload, "bad-signature"))
                .thenThrow(verificationError);

        // Act + Assert
        CustomException exception = assertThrows(
                CustomException.class,
                () -> controller.handleWebhook(rawBody, "bad-signature")
        );

        assertEquals(400, exception.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals(
                "Stripe webhook signature verification failed",
                exception.getMessage()
        );
        verify(stripeGatewayService)
                .verifyAndParseWebhook(payload, "bad-signature");
        verifyNoInteractions(stripeSubscriptionService, payoutProfileService);
    }
}
