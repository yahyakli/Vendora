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
     * POST /api/orders - Convert cart to order and create Stripe PaymentIntent
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.checkout(user.getId(), request));
    }

    /**
     * POST /api/orders/checkout - Backward compatible alias for existing clients
     */
    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(orderService.checkout(user.getId(), request));
    }

    /**
     * GET /api/orders/vendor/{vendorId} - Get orders for a vendor
     */
    @GetMapping("/vendor/{vendorId}")
    public ResponseEntity<Page<Order>> getVendorOrders(
            @PathVariable Long vendorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(orderService.getVendorOrders(vendorId, pageable));
    }

    /**
     * POST /api/orders/{id}/cancel - Cancel an order before shipping
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id, user.getId()));
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
     * GET /api/orders/{id}/dispute - Get the dispute for an order
     */
    @GetMapping("/{id}/dispute")
    public ResponseEntity<Dispute> getDispute(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long id) {
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(orderService.getDispute(id, user.getId(), isAdmin));
    }

    /**
     * PUT /api/orders/{id}/dispute/resolve - Resolve a dispute (Admin only)
     */
    @PutMapping("/{id}/dispute/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dispute> resolveDispute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String action = body.getOrDefault("action", "REJECT").toString();
        String notes = body.getOrDefault("notes", "").toString();
        boolean refunded = "REFUND".equalsIgnoreCase(action);
        return ResponseEntity.ok(orderService.resolveDisputeByOrder(id, notes, refunded));
    }

    /**
     * PUT /api/orders/disputes/{disputeId}/resolve - Legacy dispute-ID route
     */
    @PutMapping("/disputes/{disputeId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dispute> resolveDisputeById(
            @PathVariable Long disputeId,
            @RequestBody Map<String, Object> body) {
        String resolution = body.getOrDefault("resolution", "").toString();
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
