package com.vendora.order.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {
    private String shippingStreet;
    private String shippingCity;
    private String shippingState;
    private String shippingZip;
    private String shippingCountry;
}
