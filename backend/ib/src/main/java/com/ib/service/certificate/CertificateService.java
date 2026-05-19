package com.ib.service.certificate;

import com.ib.crypto.CertificatePrivateKeyCipher;
import com.ib.domain.embeddable.X509SubjectFields;
import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.enums.Role;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import com.ib.domain.repository.CertificateRepository;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.certificate.CertificateDownload;
import com.ib.dto.certificate.CertificateExtensionsRequest;
import com.ib.dto.certificate.CertificateKeyUsage;
import com.ib.dto.certificate.CreateEndEntityCertificateRequest;
import com.ib.dto.certificate.CreateIntermediateCertificateRequest;
import com.ib.dto.certificate.CreateRootCertificateRequest;
import com.ib.dto.certificate.X500NameRequest;
import com.ib.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;


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
		User caller = requireCallerUser();
		authorizeRootIssuance(caller);
		validateExtensionsOrThrow(request.extensions(), CertificateType.ROOT);

		Organization organization = organizationRepository.findById(request.organizationId())
				.orElseThrow(() -> new EntityNotFoundException(
						"Organization not found: " + request.organizationId()));

		boolean alreadyHasActiveRoot = certificateRepository
				.existsByTypeAndStatus(
						CertificateType.ROOT,
						CertificateStatus.ACTIVE);
		if (alreadyHasActiveRoot) {
			throw new CertificateIssuanceException(
					"Already has an active ROOT certificate.");
		}

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
		User caller = requireCallerUser();
		validateExtensionsOrThrow(request.extensions(), CertificateType.INTERMEDIATE);

		Organization organization = organizationRepository.findById(request.organizationId())
				.orElseThrow(() -> new EntityNotFoundException(
						"Organization not found: " + request.organizationId()));
		Certificate certificateIssuer = certificateRepository.findById(request.issuerCertificateId())
				.orElseThrow(() -> new EntityNotFoundException(
						"Issuer not found: " + request.issuerCertificateId()));

		authorizeIntermediateIssuance(caller, organization, certificateIssuer);
		issuerValidator.validateForIssuing(
				certificateIssuer, request.notBefore(), request.notAfter(), CertificateType.INTERMEDIATE);

		GeneratedCertificate generated = generationService.generateIntermediate(
				request, certificateIssuer, privateKeyCipher.loadPrivateKey(certificateIssuer.getId()));

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
		User caller = requireCallerUser();
		validateExtensionsOrThrow(request.extensions(), CertificateType.END_ENTITY);

		Certificate certificateIssuer = certificateRepository.findById(request.issuerCertificateId())
				.orElseThrow(() -> new EntityNotFoundException(
						"Issuer not found: " + request.issuerCertificateId()));

		User owner = userRepository.findByEmailIgnoreCase(request.userEmail())
				.orElseThrow(() -> new EntityNotFoundException(
						"Owner user not found: " + request.userEmail()));

		authorizeEndEntityIssuance(caller, owner, certificateIssuer);
		issuerValidator.validateForIssuing(
				certificateIssuer, request.notBefore(), request.notAfter(), CertificateType.END_ENTITY);

		GeneratedCertificate generated = generationService.generateEndEntity(
				request, certificateIssuer, privateKeyCipher.loadPrivateKey(certificateIssuer.getId()));

		Certificate certificate = Certificate.builder()
				.serialNumber(generated.serialNumber())
				.type(CertificateType.END_ENTITY)
				.status(CertificateStatus.ACTIVE)
				.organization(owner.getOrganization())
				.owner(owner)
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

	@Transactional(readOnly = true)
	public CertificateDownload downloadCertificate(Long id, String format) {
		User caller = requireCallerUser();
		Certificate certificate = certificateRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id));

		authorizeDownload(caller, certificate);

		String pem = certificate.getCertificatePem();
		String serialNumber = certificate.getSerialNumber();

		if ("cer".equalsIgnoreCase(format)) {
			byte[] derBytes = convertPemToDer(pem);
			return new CertificateDownload(derBytes, "application/x-x509-ca-cert", serialNumber + ".cer");
		}

		byte[] pemBytes = pem.getBytes(StandardCharsets.UTF_8);
		return new CertificateDownload(pemBytes, "application/x-pem-file", serialNumber + ".pem");
	}

	private void authorizeDownload(User caller, Certificate certificate) {
		if (caller.getRole() == Role.ADMIN) {
			return;
		}
		if (caller.getRole() == Role.CA_USER) {
			Long callerOrgId = requireCallerOrganizationId(caller);
			if (certificate.getOrganization() == null || !callerOrgId.equals(certificate.getOrganization().getId())) {
				throw new AccessDeniedException("CA_USER may only download certificates from their own organization");
			}
			return;
		}
		if (caller.getRole() == Role.REGULAR_USER) {
			if (certificate.getOwner() == null || !caller.getId().equals(certificate.getOwner().getId())) {
				throw new AccessDeniedException("REGULAR_USER may only download their own certificates");
			}
			return;
		}
		throw new AccessDeniedException("Caller role is not permitted to download certificates");
	}

	private static byte[] convertPemToDer(String pem) {
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
			X509Certificate cert = (X509Certificate) cf.generateCertificate(
					new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
			return cert.getEncoded();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to convert certificate PEM to DER", e);
		}
	}

	private User requireCallerUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getPrincipal() == null) {
			throw new IllegalStateException("No authenticated user in security context");
		}
		AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
		return userRepository.findByEmailIgnoreCase(principal.getUsername())
				.orElseThrow(() -> new EntityNotFoundException("Authenticated user not found"));
	}

	private void authorizeRootIssuance(User caller) {
		if (caller.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only ADMIN may issue root certificates");
		}
	}

	private void authorizeIntermediateIssuance(User caller, Organization targetOrg, Certificate issuer) {
		requireCaIssuer(issuer);

		if (caller.getRole() == Role.ADMIN) {
			return;
		}
		if (caller.getRole() != Role.CA_USER) {
			throw new AccessDeniedException("Only ADMIN or CA_USER may issue intermediate certificates");
		}

		Long callerOrgId = requireCallerOrganizationId(caller);
		if (!callerOrgId.equals(targetOrg.getId())) {
			throw new AccessDeniedException("CA_USER may only issue intermediate certificates for their own organization");
		}
		if (issuer.getOrganization() == null || !callerOrgId.equals(issuer.getOrganization().getId())) {
			throw new AccessDeniedException("CA_USER may only use an issuer certificate from their own organization");
		}
	}

	private void authorizeEndEntityIssuance(User caller, User owner, Certificate issuer) {
		requireCaIssuer(issuer);

		if (caller.getRole() == Role.ADMIN) {
			return;
		}
		if (caller.getRole() == Role.CA_USER) {
			Long callerOrgId = requireCallerOrganizationId(caller);
			if (owner.getOrganization() == null || !callerOrgId.equals(owner.getOrganization().getId())) {
				throw new AccessDeniedException("CA_USER may only issue end-entity certificates for users in their organization");
			}
			if (issuer.getOrganization() == null || !callerOrgId.equals(issuer.getOrganization().getId())) {
				throw new AccessDeniedException("CA_USER may only use an issuer certificate from their own organization");
			}
			return;
		}
		if (caller.getRole() == Role.REGULAR_USER) {
			if (!caller.getId().equals(owner.getId())) {
				throw new AccessDeniedException("REGULAR_USER may only issue an end-entity certificate for themselves");
			}
			Long callerOrgId = requireCallerOrganizationId(caller);
			if (issuer.getOrganization() == null || !callerOrgId.equals(issuer.getOrganization().getId())) {
				throw new AccessDeniedException("REGULAR_USER may only use an issuer certificate from their own organization");
			}
			return;
		}

		throw new AccessDeniedException("Caller role is not permitted to issue end-entity certificates");
	}

	private static void requireCaIssuer(Certificate issuer) {
		if (issuer.getType() == CertificateType.END_ENTITY) {
			throw new AccessDeniedException("End-entity certificates cannot be used as issuers");
		}
	}

	private static Long requireCallerOrganizationId(User caller) {
		if (caller.getOrganization() == null) {
			throw new IllegalStateException("Caller has no organization");
		}
		return caller.getOrganization().getId();
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

	private static void validateExtensionsOrThrow(CertificateExtensionsRequest extensions, CertificateType type) {
		if (extensions == null) {
			return;
		}

		boolean isCaDefault = (type == CertificateType.ROOT || type == CertificateType.INTERMEDIATE);
		boolean effectiveIsCa = extensions.isCa() != null ? extensions.isCa() : isCaDefault;

		if (type == CertificateType.END_ENTITY && effectiveIsCa) {
			throw new CertificateIssuanceException("End-entity certificate extensions must have isCa=false");
		}
		if ((type == CertificateType.ROOT || type == CertificateType.INTERMEDIATE) && !effectiveIsCa) {
			throw new CertificateIssuanceException("CA certificate extensions must have isCa=true");
		}

		if (type == CertificateType.END_ENTITY && extensions.pathLenConstraint() != null) {
			throw new CertificateIssuanceException("End-entity certificate extensions must not define pathLenConstraint");
		}
		if (!effectiveIsCa && extensions.pathLenConstraint() != null) {
			throw new CertificateIssuanceException("pathLenConstraint may only be set when isCa=true");
		}

		if (extensions.keyUsages() != null) {
			if (type == CertificateType.END_ENTITY) {
				if (extensions.keyUsages().contains(CertificateKeyUsage.KEY_CERT_SIGN)
						|| extensions.keyUsages().contains(CertificateKeyUsage.CRL_SIGN)) {
					throw new CertificateIssuanceException("End-entity certificate key usages must not include KEY_CERT_SIGN or CRL_SIGN");
				}
			} else {
				if (!extensions.keyUsages().contains(CertificateKeyUsage.KEY_CERT_SIGN)
						|| !extensions.keyUsages().contains(CertificateKeyUsage.CRL_SIGN)) {
					throw new CertificateIssuanceException("CA certificate key usages must include KEY_CERT_SIGN and CRL_SIGN");
				}
			}
		}
	}
}
