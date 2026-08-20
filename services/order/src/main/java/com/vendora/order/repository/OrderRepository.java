package com.vendora.order.repository;

import com.vendora.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    
    Optional<Order> findByStripePaymentIntentId(String stripePaymentIntentId);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i WHERE i.vendorId = :vendorId ORDER BY o.createdAt DESC")
    Page<Order> findByVendorId(@Param("vendorId") Long vendorId, Pageable pageable);
}
