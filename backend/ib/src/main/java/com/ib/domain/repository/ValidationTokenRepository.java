package com.ib.domain.repository;

import com.ib.domain.entity.ValidationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ValidationTokenRepository extends JpaRepository<ValidationToken, Long> {
    Optional<ValidationToken> findByToken(String token);
}
