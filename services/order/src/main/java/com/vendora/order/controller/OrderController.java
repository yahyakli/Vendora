package com.vendora.order.controller;

import com.vendora.order.dto.CreateOrderRequest;
import com.vendora.order.entity.*;
import com.vendora.order.security.UserPrincipal;
import com.vendora.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping({"/api/orders", "/orders"})
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * GET /api/orders - Get the current user's orders
     */
    @GetMapping
    public ResponseEntity<Page<Order>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(orderService.getUserOrders(user.getId(), pageable));
    }

    /**
     * GET /api/orders/{id} - Get an order by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id, user.getId()));
    }

    /**
     * POST /api/orders/checkout - Convert cart to order and create Stripe PaymentIntent
     */
    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.checkout(user.getId(), request));
    }

    /**
     * PUT /api/orders/{id}/status - Update order status (Admin or seller)
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(body.get("status").toUpperCase());
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SELLER"));
        return ResponseEntity.ok(orderService.updateStatus(id, newStatus, user.getId(), isAdmin));
    }

    /**
     * POST /api/orders/{id}/refund - Process a refund (Admin only)
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Refund> refund(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = body.containsKey("amount")
                ? new BigDecimal(body.get("amount").toString()) : null;
        String reason = (String) body.getOrDefault("reason", "requested_by_customer");
        return ResponseEntity.ok(orderService.processRefund(id, amount, reason, user.getId(), true));
    }

    /**
     * POST /api/orders/{id}/dispute - Open a dispute on an order
     */
    @PostMapping("/{id}/dispute")
    public ResponseEntity<Dispute> openDispute(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(orderService.openDispute(id, reason, user.getId()));
    }

    /**
     * PUT /api/orders/disputes/{disputeId}/resolve - Resolve a dispute (Admin only)
     */
    @PutMapping("/disputes/{disputeId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dispute> resolveDispute(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long disputeId,
            @RequestBody Map<String, Object> body) {
        String resolution = (String) body.getOrDefault("resolution", "");
        boolean refunded = Boolean.parseBoolean(body.getOrDefault("refunded", "false").toString());
        return ResponseEntity.ok(orderService.resolveDispute(disputeId, resolution, refunded, true));
    }

    /**
     * GET /api/orders/payouts/vendor/{vendorId} - Get vendor payouts
     */
    @GetMapping("/payouts/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<?> getPayouts(@PathVariable Long vendorId) {
        return ResponseEntity.ok(orderService.getVendorPayouts(vendorId));
    }
}
