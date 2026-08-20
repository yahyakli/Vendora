package com.vendora.order.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.vendora.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/orders/webhook", "/orders/webhook"})
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final OrderService orderService;

    @Value("${vendora.stripe.webhook-secret:whsec_placeholder}")
    private String webhookSecret;

    /**
     * POST /api/orders/webhook/stripe - Stripe webhook receiver
     */
    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        Event event;

        // Validate webhook signature if configured
        if (sigHeader != null && webhookSecret != null && !webhookSecret.contains("placeholder")) {
            try {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            } catch (SignatureVerificationException e) {
                log.error("Stripe webhook signature verification failed: {}", e.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid signature"));
            }
        } else {
            // Parse without verification (local dev mode)
            try {
                event = Event.GSON.fromJson(payload, Event.class);
            } catch (Exception e) {
                log.error("Failed to parse Stripe event payload: {}", e.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid payload"));
            }
        }

        log.info("Received Stripe event: {}", event.getType());

        // Handle payment_intent events
        if (event.getType().startsWith("payment_intent.")) {
            Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();
            stripeObject.ifPresent(obj -> {
                if (obj instanceof PaymentIntent pi) {
                    orderService.handleStripeWebhook(pi.getId(), event.getType());
                }
            });
        }

        return ResponseEntity.ok(Map.of("received", "true"));
    }
}
