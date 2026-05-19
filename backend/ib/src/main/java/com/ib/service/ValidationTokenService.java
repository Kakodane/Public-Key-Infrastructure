package com.ib.service;

import com.ib.domain.entity.User;
import com.ib.domain.entity.ValidationToken;
import com.ib.domain.enums.ValidationTokenType;
import com.ib.domain.repository.ValidationTokenRepository;
import com.ib.exception.InvalidTokenException;
import com.ib.exception.TokenExpiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ValidationTokenService {
    private final ValidationTokenRepository validationTokenRepository;

    @Transactional
    public ValidationToken createToken(User user, ValidationTokenType type, String challengePlain, String challengeEncrypted) {
        String token = UUID.randomUUID().toString();
        ValidationToken validationToken = ValidationToken.builder()
                .token(token)
                .user(user)
                .type(type)
                .challengePlain(challengePlain)
                .challengeEncrypted(challengeEncrypted)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();
        return validationTokenRepository.save(validationToken);
    }

    @Transactional
    public ValidationToken findAndValidate(String token) {
        ValidationToken foundToken = validationTokenRepository.findByToken(token).orElseThrow(() -> new InvalidTokenException("Token not found"));
        if (foundToken.isUsed()) {
            throw new InvalidTokenException("Token already used");
        }
        if (foundToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Token expired");
        }
        foundToken.setUsed(true);
        return validationTokenRepository.save(foundToken);
    }

    @Transactional(readOnly = true)
    public ValidationToken findActive(String token, ValidationTokenType expectedType) {
        ValidationToken found = validationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Token not found"));
        if (found.getType() != expectedType) {
            throw new InvalidTokenException("Token type mismatch");
        }
        if (found.isUsed()) {
            throw new InvalidTokenException("Token already used");
        }
        if (found.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Token expired");
        }
        return found;
    }
}
