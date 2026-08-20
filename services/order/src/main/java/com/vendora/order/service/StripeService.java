package com.vendora.order.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class StripeService {

    @Value("${vendora.stripe.secret-key:sk_test_placeholder}")
    private String secretKey;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.contains("placeholder")) {
            Stripe.apiKey = secretKey;
        }
    }

    public PaymentIntent createPaymentIntent(BigDecimal amount, String currency, Long orderId, Long userId) throws StripeException {
        // Convert to cents (e.g. $10.50 -> 1050 cents)
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        if (secretKey == null || secretKey.contains("placeholder")) {
            // Mock payment intent for local dev without Stripe credentials
            log.warn("Using mock Stripe PaymentIntent because valid STRIPE_SECRET_KEY is not configured.");
            PaymentIntent mockIntent = new PaymentIntent();
            mockIntent.setId("pi_mock_" + System.currentTimeMillis() + "_" + orderId);
            mockIntent.setClientSecret("pi_mock_secret_" + System.currentTimeMillis());
            mockIntent.setStatus("requires_payment_method");
            return mockIntent;
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("order_id", String.valueOf(orderId));
        metadata.put("user_id", String.valueOf(userId));

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .putAllMetadata(metadata)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                )
                .build();

        return PaymentIntent.create(params);
    }

    public Refund processRefund(String paymentIntentId, BigDecimal amount, String reason) throws StripeException {
        if (secretKey == null || secretKey.contains("placeholder") || paymentIntentId.startsWith("pi_mock_")) {
            log.warn("Mocking Stripe refund for PaymentIntent {}", paymentIntentId);
            Refund mockRefund = new Refund();
            mockRefund.setId("re_mock_" + System.currentTimeMillis());
            mockRefund.setStatus("succeeded");
            return mockRefund;
        }

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amountInCents)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .build();

        return Refund.create(params);
    }
}
