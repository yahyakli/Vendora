package com.vendora.order.controller;

import com.vendora.order.dto.CartItemRequest;
import com.vendora.order.entity.Cart;
import com.vendora.order.security.UserPrincipal;
import com.vendora.order.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/cart", "/cart"})
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * GET /api/cart - Get the current user's cart
     */
    @GetMapping
    public ResponseEntity<Cart> getCart(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(cartService.getCart(user.getId()));
    }

    /**
     * POST /api/cart/items - Add item to cart
     */
    @PostMapping("/items")
    public ResponseEntity<Cart> addItem(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(user.getId(), request));
    }

    /**
     * PUT /api/cart/items/{itemId} - Update item quantity
     */
    @PutMapping("/items/{itemId}")
    public ResponseEntity<Cart> updateItem(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long itemId,
            @RequestBody Map<String, Integer> body) {
        Integer quantity = body.get("quantity");
        if (quantity == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(cartService.updateItem(user.getId(), itemId, quantity));
    }

    /**
     * DELETE /api/cart/items/{itemId} - Remove item from cart
     */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Cart> removeItem(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(cartService.removeItem(user.getId(), itemId));
    }

    /**
     * DELETE /api/cart - Clear the cart
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearCart(@AuthenticationPrincipal UserPrincipal user) {
        cartService.clearCart(user.getId());
        return ResponseEntity.ok(Map.of("message", "Cart cleared successfully"));
    }
}
