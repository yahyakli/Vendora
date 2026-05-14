package com.vendora.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_bans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String reason;

    @CreationTimestamp
    private LocalDateTime bannedAt;

    private LocalDateTime expiresAt;
}
