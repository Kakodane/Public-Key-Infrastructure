package com.ib.service.certificate;

import com.ib.crypto.CertificatePrivateKeyCipher;
import com.ib.domain.embeddable.X509SubjectFields;
import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.Organization;
import com.ib.domain.entity.User;
import com.ib.domain.enums.RevocationReason;
import com.ib.domain.enums.Role;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import com.ib.domain.repository.CertificateRepository;
import com.ib.domain.repository.OrganizationRepository;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.certificate.*;
import com.ib.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CertificateService {

	private final CertificateGenerationService generationService;
	private final CertificateIssuerValidator issuerValidator;
	private final CertificatePrivateKeyCipher privateKeyCipher;
	private final CertificateKeystoreExportService keystoreExportService;
	private final OrganizationRepository organizationRepository;
	private final CertificateRepository certificateRepository;
	private final UserRepository userRepository;
    private final CrlService crlService;

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
	public List<GetIssuerCertificateResponse> getAvailableIssuers() {
		User caller = requireCallerUser();
		List<Certificate> candidates;
		if (caller.getRole() == Role.ADMIN) {
			candidates = certificateRepository.findAll();
		} else {
			if (caller.getOrganization() == null) {
				return List.of();
			}
			candidates = certificateRepository.findAllByOrganization_Id(caller.getOrganization().getId());
		}
		return candidates.stream()
				.filter(c -> c.getType() != CertificateType.END_ENTITY)
				.filter(c -> c.getStatus() == CertificateStatus.ACTIVE)
				.map(GetIssuerCertificateResponse::new)
				.collect(Collectors.toList());
	}

	@Transactional
	public GetCertificateResponse issueCsrCertificate(CreateCsrCertificateRequest request) {
		User caller = requireCallerUser();

		Certificate issuer = certificateRepository.findById(request.issuerCertificateId())
				.orElseThrow(() -> new EntityNotFoundException("Issuer not found: " + request.issuerCertificateId()));

		authorizeEndEntityIssuance(caller, caller, issuer);
		issuerValidator.validateForIssuing(issuer, request.notBefore(), request.notAfter(), CertificateType.END_ENTITY);

		PKCS10CertificationRequest csr = parseCsr(request.csrPem());

		GeneratedCertificate generated = generationService.generateFromCsr(
				csr, issuer, privateKeyCipher.loadPrivateKey(issuer.getId()),
				request.notBefore(), request.notAfter());

		Certificate certificate = Certificate.builder()
				.serialNumber(generated.serialNumber())
				.type(CertificateType.END_ENTITY)
				.status(CertificateStatus.ACTIVE)
				.organization(caller.getOrganization())
				.owner(caller)
				.issuer(issuer)
				.subject(toSubjectFieldsFromX500Name(csr.getSubject()))
				.notBefore(request.notBefore())
				.notAfter(request.notAfter())
				.certificatePem(generated.certificatePem())
				.build();

		Certificate saved = certificateRepository.save(certificate);
		return new GetCertificateResponse(saved);
	}

	@Transactional
	public AutogenerateEeCertificateResponse autogenerateEeCertificate(AutogenerateEeCertificateRequest request) {
		User caller = requireCallerUser();

		Certificate issuer = certificateRepository.findById(request.issuerCertificateId())
				.orElseThrow(() -> new EntityNotFoundException("Issuer not found: " + request.issuerCertificateId()));

		authorizeEndEntityIssuance(caller, caller, issuer);
		issuerValidator.validateForIssuing(issuer, request.notBefore(), request.notAfter(), CertificateType.END_ENTITY);

		CreateEndEntityCertificateRequest eeRequest = new CreateEndEntityCertificateRequest(
				request.issuerCertificateId(),
				caller.getEmail(),
				request.subject(),
				request.notBefore(),
				request.notAfter(),
				null
		);

		GeneratedCertificate generated = generationService.generateEndEntity(
				eeRequest, issuer, privateKeyCipher.loadPrivateKey(issuer.getId()));

		Certificate certificate = Certificate.builder()
				.serialNumber(generated.serialNumber())
				.type(CertificateType.END_ENTITY)
				.status(CertificateStatus.ACTIVE)
				.organization(caller.getOrganization())
				.owner(caller)
				.issuer(issuer)
				.subject(toSubjectFields(request.subject()))
				.notBefore(request.notBefore())
				.notAfter(request.notAfter())
				.certificatePem(generated.certificatePem())
				.build();
		// Intentionally NOT attaching private key — it is returned ephemerally in the response

		Certificate saved = certificateRepository.save(certificate);

		String format = request.keystoreFormat().trim().toLowerCase();
		byte[] keystoreBytes = keystoreExportService.exportEphemeralKeystore(
				generated.pkcs8PrivateKey(),
				saved,
				format,
				request.keystorePassword().toCharArray()
		);

		String ext = "jks".equals(format) ? ".jks" : ".p12";
		String keystoreBase64 = Base64.getEncoder().encodeToString(keystoreBytes);

		return new AutogenerateEeCertificateResponse(
				new GetCertificateResponse(saved),
				keystoreBase64,
				saved.getSerialNumber() + ext
		);
	}

	@Transactional(readOnly = true)
	public CertificateDownload downloadCertificate(Long id, String format) {
		User caller = requireCallerUser();
		Certificate certificate = certificateRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id));

		authorizeDownload(caller, certificate);
		return buildPublicCertificateDownload(certificate, validatePublicDownloadFormat(format));
	}

	@Transactional(readOnly = true)
	public CertificateDownload downloadCertificate(Long id, DownloadCertificateRequest request) {
		User caller = requireCallerUser();
		Certificate certificate = certificateRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id));

		authorizeDownload(caller, certificate);

		String format = request.format().trim().toLowerCase();
		if ("pkcs12".equals(format)) {
			char[] password = request.keystorePassword().toCharArray();
			byte[] p12 = keystoreExportService.exportPkcs12(certificate, password);
			return new CertificateDownload(
					p12,
					"application/x-pkcs12",
					certificate.getSerialNumber() + ".p12");
		}

		return buildPublicCertificateDownload(certificate, validatePublicDownloadFormat(format));
	}

	private static CertificateDownload buildPublicCertificateDownload(Certificate certificate, String format) {
		String pem = certificate.getCertificatePem();
		String serialNumber = certificate.getSerialNumber();

		if ("cer".equals(format)) {
			byte[] derBytes = convertPemToDer(pem);
			return new CertificateDownload(derBytes, "application/x-x509-ca-cert", serialNumber + ".cer");
		}

		byte[] pemBytes = pem.getBytes(StandardCharsets.UTF_8);
		return new CertificateDownload(pemBytes, "application/x-pem-file", serialNumber + ".pem");
	}

	private static String validatePublicDownloadFormat(String format) {
		if (format == null || format.isBlank()) {
			throw new IllegalArgumentException("format is required");
		}
		String normalized = format.trim().toLowerCase();
		if (!"pem".equals(normalized) && !"cer".equals(normalized)) {
			throw new IllegalArgumentException("format must be pem or cer");
		}
		return normalized;
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

	private static PKCS10CertificationRequest parseCsr(String csrPem) {
		try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
			Object obj = parser.readObject();
			if (!(obj instanceof PKCS10CertificationRequest csr)) {
				throw new CertificateIssuanceException("Invalid CSR: expected PKCS#10 format");
			}
			return csr;
		} catch (CertificateIssuanceException e) {
			throw e;
		} catch (Exception e) {
			throw new CertificateIssuanceException("Failed to parse CSR PEM: " + e.getMessage());
		}
	}

	private static X509SubjectFields toSubjectFieldsFromX500Name(X500Name name) {
		return X509SubjectFields.builder()
				.commonName(getRdnValue(name, BCStyle.CN))
				.organization(getRdnValue(name, BCStyle.O))
				.organizationalUnit(getRdnValue(name, BCStyle.OU))
				.country(getRdnValue(name, BCStyle.C))
				.emailAddress(getRdnValue(name, BCStyle.E))
				.build();
	}

	private static String getRdnValue(X500Name name, org.bouncycastle.asn1.ASN1ObjectIdentifier oid) {
		org.bouncycastle.asn1.x500.RDN[] rdns = name.getRDNs(oid);
		if (rdns.length == 0) return null;
		return IETFUtils.valueToString(rdns[0].getFirst().getValue());
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

    @Transactional
    public GetCertificateResponse revoke(Long id, RevokeCertificateRequest request) {
        User caller = requireCallerUser();
        Certificate certificate = certificateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id));

        authorizeDownload(caller, certificate);

        if (certificate.getStatus() == CertificateStatus.REVOKED) {
            throw new CertificateIssuanceException("Certificate is already revoked.");
        }

        Instant now = Instant.now();
        java.util.Set<Long> affectedCaIds = new java.util.HashSet<>();

        revokeSingle(certificate, request.reason(), now);
        if (certificate.getIssuer() != null) {
            affectedCaIds.add(certificate.getIssuer().getId());
        }

        if (certificate.getType() != CertificateType.END_ENTITY) {
            cascadeRevoke(certificate, now, affectedCaIds);
        }

        Certificate saved = certificateRepository.save(certificate);

        for (Long caId : affectedCaIds) {
            crlService.regenerateForCa(caId);
        }

        return new GetCertificateResponse(saved);
    }

    private static void revokeSingle(Certificate certificate, RevocationReason reason, Instant when) {
        certificate.setStatus(CertificateStatus.REVOKED);
        certificate.setRevokedAt(when);
        certificate.setRevocationReason(reason);
    }

    private void cascadeRevoke(Certificate ca, Instant when, java.util.Set<Long> affectedCaIds) {
        java.util.Deque<Certificate> stack = new java.util.ArrayDeque<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        stack.push(ca);
        while (!stack.isEmpty()) {
            Certificate current = stack.pop();
            if (current.getId() != null && !visited.add(current.getId())) {
                continue;
            }
            for (Certificate child : certificateRepository.findAllByIssuer_Id(current.getId())) {
                if (child.getId() != null && child.getId().equals(current.getId())) {
                    continue;
                }
                if (child.getStatus() != CertificateStatus.REVOKED) {
                    revokeSingle(child, RevocationReason.CESSATION_OF_OPERATION, when);
                    certificateRepository.save(child);
                    affectedCaIds.add(current.getId());
                }
                stack.push(child);
            }
        }
    }

}
