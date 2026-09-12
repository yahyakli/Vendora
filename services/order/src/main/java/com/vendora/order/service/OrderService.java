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
import java.util.UUID;

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
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> getVendorOrders(Long vendorId, Pageable pageable) {
        return orderRepository.findByVendorId(vendorId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
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

    @Transactional(rollbackFor = Exception.class)
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
            throw new IllegalStateException(
                    "Stripe error creating PaymentIntent for order " + savedOrder.getId(), e);
        }

        cart.getItems().clear();
        cartRepository.save(cart);

        return savedOrder;
    }

    @Transactional
    public Order cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        if (order.getStatus() == Order.OrderStatus.SHIPPED || order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new RuntimeException("Order cannot be cancelled after it has shipped");
        }

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            return order;
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
        return orderRepository.save(order);
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
                    generateDigitalLicenseKeys(order);
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

    private void generateDigitalLicenseKeys(Order order) {
        order.getItems().stream()
                .filter(item -> "digital".equalsIgnoreCase(item.getProductType()))
                .filter(item -> item.getDigitalLicenseKey() == null || item.getDigitalLicenseKey().isBlank())
                .forEach(item -> item.setDigitalLicenseKey("VENDORA-" + UUID.randomUUID().toString().toUpperCase()));
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
            if (refundAmount.compareTo(BigDecimal.ZERO) <= 0
                    || refundAmount.compareTo(order.getTotalAmount()) > 0) {
                throw new RuntimeException("Refund amount must be greater than zero and no more than the order total");
            }

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

    @Transactional(readOnly = true)
    public Dispute getDispute(Long orderId, Long userId, boolean isAdmin) {
        Dispute dispute = disputeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        if (!isAdmin && !dispute.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        return dispute;
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
    public Dispute resolveDisputeByOrder(Long orderId, String resolution, boolean refunded) {
        Dispute dispute = disputeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        return resolveDispute(dispute.getId(), resolution, refunded, true);
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

    @Transactional
    public Payout processVendorPayout(Long vendorId) {
        List<OrderItem> eligibleItems = orderItemRepository.findPayoutEligibleItems(
                vendorId,
                List.of(Order.OrderStatus.DELIVERED));

        BigDecimal eligibleTotal = eligibleItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal alreadyPaid = payoutRepository.findByVendorIdOrderByCreatedAtDesc(vendorId).stream()
                .filter(payout -> payout.getStatus() == Payout.PayoutStatus.PAID)
                .map(Payout::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal payableAmount = eligibleTotal.subtract(alreadyPaid);
        if (payableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("No payable balance found for vendor");
        }

        Payout payout = Payout.builder()
                .vendorId(vendorId)
                .amount(payableAmount)
                .status(Payout.PayoutStatus.PROCESSING)
                .periodEnd(LocalDateTime.now())
                .build();
        payout = payoutRepository.save(payout);

        payout.setStatus(Payout.PayoutStatus.PAID);
        payout.setProcessedAt(LocalDateTime.now());
        return payoutRepository.save(payout);
    }
}
