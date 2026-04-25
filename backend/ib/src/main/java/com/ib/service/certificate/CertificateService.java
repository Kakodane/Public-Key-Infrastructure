package com.ib.service.certificate;

import com.ib.crypto.CertificatePrivateKeyCipher;
import com.ib.domain.embeddable.X509SubjectFields;
import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import com.ib.domain.repository.CertificateRepository;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.certificate.CreateEndEntityCertificateRequest;
import com.ib.dto.certificate.CreateIntermediateCertificateRequest;
import com.ib.dto.certificate.CreateRootCertificateRequest;
import com.ib.dto.certificate.X500NameRequest;
import com.ib.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class CertificateService {

	private final CertificateGenerationService generationService;
	private final CertificateIssuerValidator issuerValidator;
	private final CertificatePrivateKeyCipher privateKeyCipher;
	private final OrganizationRepository organizationRepository;
	private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;

	@Transactional
	public GetCertificateResponse issueRoot(CreateRootCertificateRequest request) {
		Organization organization = organizationRepository.findById(request.organizationId()).orElseThrow(() -> new EntityNotFoundException(
                "Organization not found: " + request.organizationId()));

		GeneratedCertificate generated = generationService.generateRoot(request);

		Certificate certificate = Certificate.builder()
				.serialNumber(generated.serialNumber())
				.type(CertificateType.ROOT)
				.status(CertificateStatus.ACTIVE)
				.organization(organization)
				.owner(null)
				.issuer(null)
				.subject(toSubjectFields(request.subject()))
				.notBefore(request.notBefore())
				.notAfter(request.notAfter())
				.certificatePem(generated.certificatePem())
				.build();

		privateKeyCipher.attachEncryptedPrivateKey(certificate, generated.pkcs8PrivateKey());

		Certificate saved = certificateRepository.save(certificate);
		return new GetCertificateResponse(saved);
	}

	@Transactional
	public GetCertificateResponse issueIntermediate(CreateIntermediateCertificateRequest request) {
        Organization organization = organizationRepository.findById(request.organizationId()).orElseThrow(() -> new EntityNotFoundException(
                "Organization not found: " + request.organizationId()));
        Certificate certificateIssuer= certificateRepository.findById(request.issuerCertificateId()).orElseThrow(()-> new EntityNotFoundException(
                "Issuer not found: "+ request.issuerCertificateId()));
        issuerValidator.validateForIssuing(certificateIssuer,request.notBefore(),request.notAfter());

        GeneratedCertificate generated = generationService.generateIntermediate(request, certificateIssuer,privateKeyCipher.loadPrivateKey(certificateIssuer.getId()));


        Certificate certificate = Certificate.builder()
                .serialNumber(generated.serialNumber())
                .type(CertificateType.INTERMEDIATE)
                .status(CertificateStatus.ACTIVE)
                .organization(organization)
                .owner(null)
                .issuer(certificateIssuer)
                .subject(toSubjectFields(request.subject()))
                .notBefore(request.notBefore())
                .notAfter(request.notAfter())
                .certificatePem(generated.certificatePem())
                .build();

        privateKeyCipher.attachEncryptedPrivateKey(certificate, generated.pkcs8PrivateKey());

        Certificate saved = certificateRepository.save(certificate);
        return new GetCertificateResponse(saved);
	}

	@Transactional
	public GetCertificateResponse issueEndEntity(CreateEndEntityCertificateRequest request) {
        Certificate certificateIssuer= certificateRepository.findById(request.issuerCertificateId()).orElseThrow(()-> new EntityNotFoundException(
                "Issuer not found: "+ request.issuerCertificateId()));
        issuerValidator.validateForIssuing(certificateIssuer,request.notBefore(),request.notAfter());

        GeneratedCertificate generated = generationService.generateEndEntity(request, certificateIssuer,privateKeyCipher.loadPrivateKey(certificateIssuer.getId()));


        Optional<User> user= userRepository.findById(request.ownerUserId());

        Certificate certificate = Certificate.builder()
                .serialNumber(generated.serialNumber())
                .type(CertificateType.END_ENTITY)
                .status(CertificateStatus.ACTIVE)
                .organization(user.get().getOrganization())
                .owner(user.get())
                .issuer(certificateIssuer)
                .subject(toSubjectFields(request.subject()))
                .notBefore(request.notBefore())
                .notAfter(request.notAfter())
                .certificatePem(generated.certificatePem())
                .build();

        privateKeyCipher.attachEncryptedPrivateKey(certificate, generated.pkcs8PrivateKey());

        Certificate saved = certificateRepository.save(certificate);
        return new GetCertificateResponse(saved);
	}
	private static X509SubjectFields toSubjectFields(X500NameRequest r) {
		return X509SubjectFields.builder()
				.commonName(r.commonName())
				.organization(r.organization())
				.organizationalUnit(r.organizationalUnit())
				.country(r.country())
				.emailAddress(r.emailAddress())
				.build();
	}
}
