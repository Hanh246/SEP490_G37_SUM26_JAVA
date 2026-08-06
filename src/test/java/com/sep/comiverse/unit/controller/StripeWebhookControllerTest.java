package com.sep.comiverse.unit.controller;

import com.sep.comiverse.controller.StripeWebhookController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep.comiverse.service.StripeGatewayService;
import com.sep.comiverse.service.StripeSubscriptionService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StripeWebhookControllerTest {

    @Test
    void verifiedWebhookIsProcessedBeforeAcknowledgement() throws Exception {
        StripeGatewayService gateway = mock(StripeGatewayService.class);
        StripeSubscriptionService subscriptionService = mock(StripeSubscriptionService.class);
        com.sep.comiverse.service.CreatorStripePayoutProfileService payoutProfileService = mock(com.sep.comiverse.service.CreatorStripePayoutProfileService.class);
        StripeWebhookController controller = new StripeWebhookController(gateway, subscriptionService, payoutProfileService);
        byte[] payload = """
                {"id":"evt_123","type":"invoice.paid","data":{"object":{}}}
                """.getBytes(StandardCharsets.UTF_8);
        JsonNode event = new ObjectMapper().readTree(payload);
        when(gateway.verifyAndParseWebhook(new String(payload, StandardCharsets.UTF_8), "signature"))
                .thenReturn(event);

        controller.handleWebhook(payload, "signature");

        verify(subscriptionService).processWebhook(event);
    }
}
