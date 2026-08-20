package com.vendora.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemRequest {
    @NotNull
    private Long productId;

    @NotNull
    private Long vendorId;

    @NotNull
    private String productName;

    @NotNull
    @Min(0)
    private BigDecimal unitPrice;

    @NotNull
    @Min(1)
    private Integer quantity;

    private String productType = "physical";
}
