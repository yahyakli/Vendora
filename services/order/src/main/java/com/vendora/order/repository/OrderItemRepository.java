package com.vendora.order.repository;

import com.vendora.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

import com.vendora.order.entity.Order.OrderStatus;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByVendorId(Long vendorId);

        @Query("SELECT i FROM OrderItem i JOIN FETCH i.order o "
            + "WHERE i.vendorId = :vendorId AND o.status IN :statuses")
        List<OrderItem> findPayoutEligibleItems(
            @Param("vendorId") Long vendorId,
            @Param("statuses") List<OrderStatus> statuses);
}
