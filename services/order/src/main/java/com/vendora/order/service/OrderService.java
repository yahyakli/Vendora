package com.vendora.order.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.vendora.order.dto.CreateOrderRequest;
import com.vendora.order.entity.*;
import com.vendora.order.event.OrderEventPublisher;
import com.vendora.order.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final OrderEventPublisher eventPublisher;
    private final StripeService stripeService;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final DisputeRepository disputeRepository;
    private final PayoutRepository payoutRepository;

    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied: Order does not belong to this user");
        }
        return order;
    }

    @Transactional
    public Order checkout(Long userId, CreateOrderRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart is empty or not found"));

        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cannot checkout an empty cart");
        }

        BigDecimal subtotal = cart.getSubtotal();
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(shippingFee).add(tax);

        Order order = Order.builder()
                .userId(userId)
                .status(Order.OrderStatus.PENDING)
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .tax(tax)
                .totalAmount(totalAmount)
                .shippingStreet(request.getShippingStreet())
                .shippingCity(request.getShippingCity())
                .shippingState(request.getShippingState())
                .shippingZip(request.getShippingZip())
                .shippingCountry(request.getShippingCountry())
                .paymentStatus(Order.PaymentStatus.PENDING)
                .build();

        // Convert cart items to order items
        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> OrderItem.builder()
                        .order(order)
                        .productId(cartItem.getProductId())
                        .vendorId(cartItem.getVendorId())
                        .productName(cartItem.getProductName())
                        .unitPrice(cartItem.getUnitPrice())
                        .quantity(cartItem.getQuantity())
                        .totalPrice(cartItem.getTotalPrice())
                        .productType(cartItem.getProductType())
                        .build())
                .toList();

        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        // Create Stripe PaymentIntent
        try {
            PaymentIntent intent = stripeService.createPaymentIntent(totalAmount, "usd", savedOrder.getId(), userId);
            savedOrder.setStripePaymentIntentId(intent.getId());
            savedOrder.setClientSecret(intent.getClientSecret());
            orderRepository.save(savedOrder);

            // Save payment record
            paymentRepository.save(Payment.builder()
                    .orderId(savedOrder.getId())
                    .userId(userId)
                    .amount(totalAmount)
                    .currency("usd")
                    .stripePaymentIntentId(intent.getId())
                    .status("pending")
                    .build());

        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent for order {}: {}", savedOrder.getId(), e.getMessage());
        }

        // Clear the cart
        cart.getItems().clear();
        cartRepository.save(cart);

        return savedOrder;
    }

    @Transactional
    public Order updateStatus(Long orderId, Order.OrderStatus newStatus, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        if (newStatus == Order.OrderStatus.SHIPPED) {
            Map<String, Object> event = new HashMap<>();
            event.put("order_id", orderId);
            event.put("user_id", order.getUserId());
            eventPublisher.publishOrderShipped(event);
        }

        return saved;
    }

    @Transactional
    public void handleStripeWebhook(String paymentIntentId, String eventType) {
        orderRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(order -> {
            switch (eventType) {
                case "payment_intent.succeeded" -> {
                    order.setStatus(Order.OrderStatus.PAID);
                    order.setPaymentStatus(Order.PaymentStatus.SUCCEEDED);
                    orderRepository.save(order);

                    // Update payment record
                    paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                            .ifPresent(p -> {
                                p.setStatus("succeeded");
                                paymentRepository.save(p);
                            });

                    // Publish event
                    Map<String, Object> event = new HashMap<>();
                    event.put("order_id", order.getId());
                    event.put("user_id", order.getUserId());
                    event.put("total_amount", order.getTotalAmount());
                    eventPublisher.publishOrderPlaced(event);

                    log.info("Order {} marked as PAID after Stripe PaymentIntent succeeded", order.getId());
                }
                case "payment_intent.payment_failed" -> {
                    order.setPaymentStatus(Order.PaymentStatus.FAILED);
                    orderRepository.save(order);

                    paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                            .ifPresent(p -> {
                                p.setStatus("failed");
                                paymentRepository.save(p);
                            });

                    log.warn("Order {} payment failed", order.getId());
                }
                default -> log.debug("Unhandled Stripe event type: {}", eventType);
            }
        });
    }

    @Transactional
    public Refund processRefund(Long orderId, BigDecimal amount, String reason, Long userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        if (order.getStripePaymentIntentId() == null) {
            throw new RuntimeException("No payment found for this order");
        }

        try {
            BigDecimal refundAmount = amount != null ? amount : order.getTotalAmount();
            com.stripe.model.Refund stripeRefund = stripeService.processRefund(
                    order.getStripePaymentIntentId(), refundAmount, reason);

            Refund refund = refundRepository.save(Refund.builder()
                    .orderId(orderId)
                    .amount(refundAmount)
                    .reason(reason)
                    .stripeRefundId(stripeRefund.getId())
                    .build());

            order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
            order.setStatus(Order.OrderStatus.CANCELLED);
            orderRepository.save(order);

            Map<String, Object> event = new HashMap<>();
            event.put("order_id", orderId);
            event.put("user_id", order.getUserId());
            event.put("amount", refundAmount);
            eventPublisher.publishOrderRefunded(event);

            return refund;

        } catch (StripeException e) {
            throw new RuntimeException("Refund failed: " + e.getMessage());
        }
    }

    @Transactional
    public Dispute openDispute(Long orderId, String reason, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        if (disputeRepository.findByOrderId(orderId).isPresent()) {
            throw new RuntimeException("A dispute already exists for this order");
        }

        return disputeRepository.save(Dispute.builder()
                .orderId(orderId)
                .userId(userId)
                .reason(reason)
                .status(Dispute.DisputeStatus.OPEN)
                .build());
    }

    @Transactional
    public Dispute resolveDispute(Long disputeId, String resolution, boolean refunded, boolean isAdmin) {
        if (!isAdmin) {
            throw new RuntimeException("Only admins can resolve disputes");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        dispute.setStatus(refunded ? Dispute.DisputeStatus.RESOLVED_REFUNDED : Dispute.DisputeStatus.RESOLVED_DENIED);
        dispute.setResolutionNotes(resolution);
        dispute.setResolvedAt(LocalDateTime.now());

        return disputeRepository.save(dispute);
    }

    @Transactional(readOnly = true)
    public List<Payout> getVendorPayouts(Long vendorId) {
        return payoutRepository.findByVendorIdOrderByCreatedAtDesc(vendorId);
    }
}
