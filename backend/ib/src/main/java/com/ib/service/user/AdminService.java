package com.ib.service.user;

import com.ib.crypto.OrganizationKeyService;
import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.entity.ValidationToken;
import com.ib.domain.enums.ValidationTokenType;
import com.ib.domain.repository.CertificateRepository;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.user.*;
import com.ib.exception.ResourceAlreadyExistsException;
import com.ib.service.EmailService;
import com.ib.service.ValidationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final CertificateRepository certificateRepository;
    private final OrganizationRepository organizationRepository;
    private final CAService caService;
    private final ValidationTokenService validationTokenService;
    private final EmailService emailService;
    private final OrganizationKeyService organizationKeyService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<GetCertificateResponse> findAll() {
        List<Certificate> certificates = certificateRepository.findAll();
        List<GetCertificateResponse> certificateResponses = new ArrayList<>();
        for (Certificate certificate : certificates) {
            certificateResponses.add(new GetCertificateResponse(certificate));
        }
        return certificateResponses;
    }
    @Transactional
    public CreateCAUserResponse createCAUser(CreateCAUserRequest request) {
        User user = caService.createUser(request);
        ValidationToken token = validationTokenService.createToken(user, ValidationTokenType.CA_ACTIVATION,null,null);
        String activationUrl = frontendUrl + "/set-password?token=" + token.getToken();
        emailService.sendEmail(
                "v.vitomirovic@gmail.com",
                "Activate your CA user account",
                "Click the link to set your password: " + activationUrl
        );
        return new CreateCAUserResponse(user.getId());
    }

    @Transactional
    public CreateOrganizationResponse createOrganization(CreateOrganizationRequest request) {
        if (organizationRepository.findByNameIgnoreCase(request.name()).isPresent()) {
            throw new ResourceAlreadyExistsException("Organization with this name already exists");
        }
        Organization organization = Organization.builder()
                .name(request.name())
                .build();
        organizationKeyService.getOrCreateOrgKey(organization);
        Organization saved = organizationRepository.save(organization);
        return new CreateOrganizationResponse(saved.getId(), saved.getName());
    }
    public List<GetOrgsResponse> getOrgs(){
        List<Organization> orgs= organizationRepository.findAll();
        List<GetOrgsResponse> orgsResponses= new ArrayList<>();
        for(Organization org:orgs){
            orgsResponses.add(new GetOrgsResponse(org.getId(),org.getName()));
        }
        return  orgsResponses;
    }
}
