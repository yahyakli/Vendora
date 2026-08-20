package com.vendora.order.repository;

import com.vendora.order.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByVendorIdOrderByCreatedAtDesc(Long vendorId);
}
