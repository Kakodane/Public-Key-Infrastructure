package com.ib.service.user;

import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.entity.ValidationToken;
import com.ib.domain.enums.Role;
import com.ib.domain.repository.CertificateRepository;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.user.CreateCAUserRequest;
import com.ib.dto.user.SetPasswordRequest;
import com.ib.exception.ResourceAlreadyExistsException;
import com.ib.exception.ResourceNotFoundException;
import com.ib.security.AuthenticatedUser;
import com.ib.service.ValidationTokenService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class CAService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;
    private final ValidationTokenService validationTokenService;

    @Transactional(readOnly = true)
    public List<GetCertificateResponse> findAll() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        Optional<User> user = userRepository.findByEmailIgnoreCase(authenticatedUser.getUsername());
        if (user.isEmpty()) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }
        if (user.get().getOrganization() == null) {
            throw new IllegalStateException("CA user has no organization");
        }
        List<Certificate> certificates = certificateRepository
                .findAllByOrganization_Id(user.get().getOrganization().getId());
        List<GetCertificateResponse> certificateResponses = new ArrayList<>();
        for (Certificate certificate : certificates) {
            certificateResponses.add(new GetCertificateResponse(certificate));
        }
        return certificateResponses;
    }

    @Transactional
    public User createUser(CreateCAUserRequest userRequest) {
        if (userRepository.existsByEmailIgnoreCase(userRequest.email())) {
            throw new ResourceAlreadyExistsException("Email already exists");
        }
        Organization org = organizationRepository.findById(userRequest.orgId()).orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        String temporaryPassword = UUID.randomUUID().toString();

        User user = User.builder()
                .email(userRequest.email())
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .firstName(userRequest.firstName())
                .lastName(userRequest.lastName())
                .role(Role.CA_USER)
                .organization(org)
                .enabled(false)
                .emailVerified(false)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public void setPassword(SetPasswordRequest request) {
        ValidationToken token = validationTokenService.findAndValidate(request.token());
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepository.save(user);
    }
}
