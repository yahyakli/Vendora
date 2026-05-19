package com.vendora.auth.repository;

import com.vendora.auth.entity.RefreshToken;
import com.vendora.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUser_Id(Long userId);

    @Modifying
    int deleteByUser(User user);
}
