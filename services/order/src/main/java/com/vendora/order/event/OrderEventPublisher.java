package com.vendora.order.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderPlaced(Map<String, Object> eventData) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, eventData);
            log.info("Published event {}: {}", RabbitMQConfig.ROUTING_KEY_ORDER_PLACED, eventData.get("order_id"));
        } catch (Exception e) {
            log.error("Failed to publish order.placed event: {}", e.getMessage());
        }
    }

    public void publishOrderShipped(Map<String, Object> eventData) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_SHIPPED, eventData);
            log.info("Published event {}: {}", RabbitMQConfig.ROUTING_KEY_ORDER_SHIPPED, eventData.get("order_id"));
        } catch (Exception e) {
            log.error("Failed to publish order.shipped event: {}", e.getMessage());
        }
    }

    public void publishOrderRefunded(Map<String, Object> eventData) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_REFUNDED, eventData);
            log.info("Published event {}: {}", RabbitMQConfig.ROUTING_KEY_ORDER_REFUNDED, eventData.get("order_id"));
        } catch (Exception e) {
            log.error("Failed to publish order.refunded event: {}", e.getMessage());
        }
    }
}
