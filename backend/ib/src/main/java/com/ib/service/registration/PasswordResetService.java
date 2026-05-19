package com.ib.service.registration;

import com.ib.domain.entity.RegularUserProfile;
import com.ib.domain.entity.User;
import com.ib.domain.entity.ValidationToken;
import com.ib.domain.enums.Role;
import com.ib.domain.enums.ValidationTokenType;
import com.ib.domain.repository.RegularUserProfileRepository;
import com.ib.domain.repository.UserRepository;
import com.ib.domain.repository.ValidationTokenRepository;
import com.ib.dto.ForgotPasswordRequest;
import com.ib.dto.PasswordResetChallengeResponse;
import com.ib.dto.ResetPasswordRequest;
import com.ib.exception.InvalidTokenException;
import com.ib.service.EmailService;
import com.ib.service.RefreshTokenService;
import com.ib.service.ValidationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {


        private final UserRepository userRepository;
        private final RegularUserProfileRepository regularUserProfileRepository;
        private final PasswordEncoder passwordEncoder;
        private final ValidationTokenService validationTokenService;
        private final RegistrationChallengeService challengeService;
        private final RefreshTokenService refreshTokenService;
        private final EmailService emailService;
    private final ValidationTokenRepository validationTokenRepository;

    @Value("${app.frontend.url}")
        private String frontendUrl;


        @Transactional
        public void requestReset(ForgotPasswordRequest request) {
            String email = request.email().trim();
            Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(email);
            if (maybeUser.isEmpty()) {
                return;
            }
            User user = maybeUser.get();
            if (!user.isEnabled() || user.getRole() != Role.REGULAR_USER) {
                return;
            }
            Optional<RegularUserProfile> profile = regularUserProfileRepository.findById(user.getId());
            if (profile.isEmpty() || profile.get().getRegistrationPublicKey() == null
                    || profile.get().getRegistrationPublicKey().isBlank()) {
                return;
            }

            RSAPublicKey publicKey;
            try {
                publicKey = challengeService.parsePublicKeyPem(profile.get().getRegistrationPublicKey());
            }
            catch (IllegalArgumentException ex) {
                return;
            }
            RegistrationChallengeService.Challenge challenge = challengeService.createChallenge(publicKey);

            ValidationToken token = validationTokenService.createToken(
                    user,
                    ValidationTokenType.PASSWORD_RESET,
                    challenge.plainBase64(),
                    challenge.encryptedBase64()
            );

            String resetUrl = frontendUrl + "/reset-password?token=" + token.getToken();
            emailService.sendEmail(
                    user.getEmail(),
                    "Reset your PKI account password",
                    "Click the link to reset your password (valid for 24h): " + resetUrl
            );
        }

        @Transactional(readOnly = true)
        public PasswordResetChallengeResponse getResetChallenge(String token) {
            ValidationToken vt = validationTokenService.findActive(token, ValidationTokenType.PASSWORD_RESET);
            User user = vt.getUser();
            RegularUserProfile profile = regularUserProfileRepository.findById(user.getId())
                    .orElseThrow(() -> new InvalidTokenException("User profile missing"));
            return new PasswordResetChallengeResponse(
                    user.getEmail(),
                    profile.getRegistrationPublicKey(),
                    vt.getChallengeEncrypted()
            );
        }

        @Transactional
        public void resetPassword(ResetPasswordRequest request) {
            ValidationToken vt = validationTokenService.findActive(request.token(), ValidationTokenType.PASSWORD_RESET);
            if (!challengeService.matches(vt.getChallengePlain(), request.decryptedChallengeBase64())) {
                throw new InvalidTokenException("Challenge response is incorrect");
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new IllegalArgumentException("Password and confirmPassword do not match");
            }
            User user = vt.getUser();
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);
            vt.setUsed(true);
            validationTokenRepository.save(vt);
            refreshTokenService.revokeAllForUser(user.getId());
        }
}
