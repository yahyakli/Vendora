package com.vendora.order.controller;

import com.vendora.order.entity.Payout;
import com.vendora.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/payouts", "/payouts"})
@RequiredArgsConstructor
public class PayoutController {

    private final OrderService orderService;

    @GetMapping("/vendor/{vendorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<List<Payout>> getVendorPayouts(@PathVariable Long vendorId) {
        return ResponseEntity.ok(orderService.getVendorPayouts(vendorId));
    }

    @PostMapping("/{vendorId}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Payout> processVendorPayout(@PathVariable Long vendorId) {
        return ResponseEntity.ok(orderService.processVendorPayout(vendorId));
    }
}