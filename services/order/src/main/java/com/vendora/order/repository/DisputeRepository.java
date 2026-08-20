package com.vendora.order.repository;

import com.vendora.order.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    Optional<Dispute> findByOrderId(Long orderId);
    List<Dispute> findByUserId(Long userId);
}
