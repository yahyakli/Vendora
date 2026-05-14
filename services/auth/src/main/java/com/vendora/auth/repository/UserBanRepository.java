package com.vendora.auth.repository;

import com.vendora.auth.entity.UserBan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBanRepository extends JpaRepository<UserBan, Long> {
    List<UserBan> findByUser_Id(Long userId);
}
