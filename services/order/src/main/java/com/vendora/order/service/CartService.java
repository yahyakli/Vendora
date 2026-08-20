package com.vendora.order.service;

import com.vendora.order.dto.CartItemRequest;
import com.vendora.order.entity.Cart;
import com.vendora.order.entity.CartItem;
import com.vendora.order.repository.CartItemRepository;
import com.vendora.order.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional(readOnly = true)
    public Cart getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(
                        Cart.builder().userId(userId).build()
                ));
    }

    @Transactional
    public Cart addItem(Long userId, CartItemRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));

        cartItemRepository.findByCartIdAndProductId(cart.getId(), request.getProductId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setQuantity(existing.getQuantity() + request.getQuantity());
                            existing.setUnitPrice(request.getUnitPrice());
                            cartItemRepository.save(existing);
                        },
                        () -> {
                            CartItem item = CartItem.builder()
                                    .cart(cart)
                                    .productId(request.getProductId())
                                    .vendorId(request.getVendorId())
                                    .productName(request.getProductName())
                                    .unitPrice(request.getUnitPrice())
                                    .quantity(request.getQuantity())
                                    .productType(request.getProductType() != null ? request.getProductType() : "physical")
                                    .build();
                            cart.getItems().add(item);
                        }
                );

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart updateItem(Long userId, Long itemId, Integer quantity) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(quantity);
        }

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart removeItem(Long userId, Long itemId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cart.getItems().removeIf(item -> item.getId().equals(itemId));
        return cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cartRepository.save(cart);
        });
    }
}
