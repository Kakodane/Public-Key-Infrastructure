package com.ib.service.registration;

import com.ib.domain.entity.Organization;
import com.ib.domain.entity.RegularUserProfile;
import com.ib.domain.entity.User;
import com.ib.domain.entity.ValidationToken;
import com.ib.domain.enums.Role;
import com.ib.domain.enums.ValidationTokenType;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.RegularUserProfileRepository;
import com.ib.domain.repository.UserRepository;
import com.ib.domain.repository.ValidationTokenRepository;
import com.ib.dto.auth.ActivateAccountRequest;
import com.ib.dto.auth.ActivationChallengeResponse;
import com.ib.dto.auth.RegisterRequest;
import com.ib.exception.InvalidTokenException;
import com.ib.exception.ResourceAlreadyExistsException;
import com.ib.exception.ResourceNotFoundException;
import com.ib.service.EmailService;
import com.ib.service.ValidationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.interfaces.RSAPublicKey;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final RegularUserProfileRepository regularUserProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidationTokenService validationTokenService;
    private final RegistrationChallengeService challengeService;
    private final EmailService emailService;
    private final ValidationTokenRepository validationTokenRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public boolean register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Password and confirmPassword do not match");
        }
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceAlreadyExistsException("Email already registered");
        }
        Organization organization = organizationRepository.findByNameIgnoreCase(request.organizationName().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        RSAPublicKey publicKey = challengeService.parsePublicKeyPem(request.publicKeyPem());
        RegistrationChallengeService.Challenge challenge = challengeService.createChallenge(publicKey);

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .role(Role.REGULAR_USER)
                .organization(organization)
                .enabled(false)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        RegularUserProfile profile = RegularUserProfile.builder()
                .user(user)
                .registrationPublicKey(request.publicKeyPem())
                .build();
        regularUserProfileRepository.save(profile);

        ValidationToken token = validationTokenService.createToken(
                user,
                ValidationTokenType.REGISTRATION_ACTIVATION,
                challenge.plainBase64(),
                challenge.encryptedBase64()
        );

        String activationUrl = frontendUrl + "/activate?token=" + token.getToken();
        emailService.sendEmail(
                user.getEmail(),
                "Activate your PKI account",
                "Click the link to activate your account (valid for 24h): " + activationUrl
        );

        return true;
    }

    @Transactional(readOnly = true)
    public ActivationChallengeResponse getActivationChallenge(String token) {
        ValidationToken vt = validationTokenService.findActive(token, ValidationTokenType.REGISTRATION_ACTIVATION);
        User user = vt.getUser();
        RegularUserProfile profile = regularUserProfileRepository.findById(user.getId())
                .orElseThrow(() -> new InvalidTokenException("Registration profile missing"));
        return new ActivationChallengeResponse(
                user.getEmail(),
                profile.getRegistrationPublicKey(),
                vt.getChallengeEncrypted()
        );
    }

    @Transactional
    public void activate(ActivateAccountRequest request) {
        ValidationToken vt = validationTokenService.findActive(request.token(), ValidationTokenType.REGISTRATION_ACTIVATION);
        if (!challengeService.matches(vt.getChallengePlain(), request.decryptedChallengeBase64())) {
            throw new InvalidTokenException("Challenge response is incorrect");
        }
        User user = vt.getUser();
        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepository.save(user);
        vt.setUsed(true);
        validationTokenRepository.save(vt);
    }
}
