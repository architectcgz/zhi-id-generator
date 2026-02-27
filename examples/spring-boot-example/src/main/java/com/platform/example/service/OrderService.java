package com.platform.example.service;

import com.platform.example.model.Order;
import com.platform.example.model.OrderItem;
import com.platform.idgen.client.IdGeneratorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Example service demonstrating ID Generator usage in business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final IdGeneratorClient idGeneratorClient;

    /**
     * Create an order with generated IDs.
     * 
     * This example shows:
     * 1. Using Snowflake ID for order (time-ordered)
     * 2. Batch generating IDs for order items
     */
    public Order createOrder(Long userId, List<OrderItemRequest> itemRequests) {
        // Generate order ID using Snowflake mode
        long orderId = idGeneratorClient.nextSnowflakeId();
        log.info("Creating order with ID: {}", orderId);

        // Generate IDs for all order items in batch
        List<Long> itemIds = idGeneratorClient.nextSnowflakeIds(itemRequests.size());
        log.info("Generated {} item IDs", itemIds.size());

        // Create order
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setCreateTime(LocalDateTime.now());
        order.setStatus("PENDING");

        // Create order items
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (int i = 0; i < itemRequests.size(); i++) {
            OrderItemRequest request = itemRequests.get(i);
            
            OrderItem item = new OrderItem();
            item.setId(itemIds.get(i));
            item.setOrderId(orderId);
            item.setProductId(request.getProductId());
            item.setProductName(request.getProductName());
            item.setQuantity(request.getQuantity());
            item.setPrice(request.getPrice());
            
            BigDecimal itemTotal = request.getPrice().multiply(
                BigDecimal.valueOf(request.getQuantity())
            );
            item.setTotalPrice(itemTotal);
            totalAmount = totalAmount.add(itemTotal);
            
            items.add(item);
        }

        order.setItems(items);
        order.setTotalAmount(totalAmount);

        log.info("Order created: orderId={}, itemCount={}, totalAmount={}", 
            orderId, items.size(), totalAmount);

        // In real application, save to database here
        // orderRepository.save(order);

        return order;
    }

    /**
     * Request object for creating order items.
     */
    public static class OrderItemRequest {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;

        // Getters and Setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}
