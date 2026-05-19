package com.ib.domain.entity;

import com.ib.domain.enums.ValidationTokenType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "validation_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ValidationTokenType type;

    @Column(name = "challenge_plain", columnDefinition = "TEXT")
    private String challengePlain;

    @Column(name = "challenge_encrypted", columnDefinition = "TEXT")
    private String challengeEncrypted;


    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;
}
