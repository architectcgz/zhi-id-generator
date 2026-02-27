package com.platform.example.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Order item entity example.
 */
@Data
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
}
