package com.ib.service.certificate;

import com.ib.domain.entity.Certificate;
import com.ib.dto.certificate.CertificateExtendedKeyUsage;
import com.ib.dto.certificate.CertificateExtensionsRequest;
import com.ib.dto.certificate.CertificateKeyUsage;
import com.ib.dto.certificate.CreateEndEntityCertificateRequest;
import com.ib.dto.certificate.CreateIntermediateCertificateRequest;
import com.ib.dto.certificate.CreateRootCertificateRequest;
import com.ib.dto.certificate.X500NameRequest;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;


@Service
public class CertificateGenerationService {

	private static final int ROOT_KEY_SIZE = 4096;
	private static final int INTERMEDIATE_KEY_SIZE = 3072;
	private static final int END_ENTITY_KEY_SIZE = 2048;

	private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";
	private static final String BC_PROVIDER = "BC";
	private static final int SERIAL_BIT_LENGTH = 128;

	private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.crl.base-url}")
    private String crlBaseUrl;

	public GeneratedCertificate generateRoot(CreateRootCertificateRequest request) {
		try {
			X500Name subject = buildX500Name(request.subject());
			KeyPair keyPair = generateRsaKeyPair(ROOT_KEY_SIZE);
			BigInteger serial = nextSerial();

			JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
					subject,
					serial,
					Date.from(request.notBefore()),
					Date.from(request.notAfter()),
					subject,
					keyPair.getPublic()
			);

			JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
			addRequestedExtensions(builder, request.extensions(), true);
			builder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
			builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(keyPair.getPublic()));

			X509Certificate cert = signAndConvert(builder, keyPair.getPrivate());

			return new GeneratedCertificate(
					toPem(cert),
					keyPair.getPrivate().getEncoded(),
					serial.toString(16));
		} catch (Exception e) {
			throw new CertificateGenerationException("Failed to generate root certificate", e);
		}
	}

	public GeneratedCertificate generateIntermediate(
			CreateIntermediateCertificateRequest request,
			Certificate issuer,
			byte[] issuerPrivateKeyPkcs8
	) {
		try {
			X509Certificate issuerCert = parsePem(issuer.getCertificatePem());
			PrivateKey issuerSigningKey = reconstructRsaPrivateKey(issuerPrivateKeyPkcs8);

			X500Name subject = buildX500Name(request.subject());
			KeyPair keyPair = generateRsaKeyPair(INTERMEDIATE_KEY_SIZE);
			BigInteger serial = nextSerial();

			JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
					issuerCert,
					serial,
					Date.from(request.notBefore()),
					Date.from(request.notAfter()),
					subject,
					keyPair.getPublic()
			);

			JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
			addRequestedExtensions(builder, request.extensions(), true);
			builder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
			builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert.getPublicKey()));
            addCrlDistributionPoint(builder, issuer.getId());
			X509Certificate cert = signAndConvert(builder, issuerSigningKey);

			return new GeneratedCertificate(
					toPem(cert),
					keyPair.getPrivate().getEncoded(),
					serial.toString(16));
		} catch (CertificateGenerationException e) {
			throw e;
		} catch (Exception e) {
			throw new CertificateGenerationException("Failed to generate intermediate certificate", e);
		}
	}

	public GeneratedCertificate generateEndEntity(
			CreateEndEntityCertificateRequest request,
			Certificate issuer,
			byte[] issuerPrivateKeyPkcs8
	) {
		try {
			X509Certificate issuerCert = parsePem(issuer.getCertificatePem());
			PrivateKey issuerSigningKey = reconstructRsaPrivateKey(issuerPrivateKeyPkcs8);

			X500Name subject = buildX500Name(request.subject());
			KeyPair keyPair = generateRsaKeyPair(END_ENTITY_KEY_SIZE);
			BigInteger serial = nextSerial();

			JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
					issuerCert,
					serial,
					Date.from(request.notBefore()),
					Date.from(request.notAfter()),
					subject,
					keyPair.getPublic()
			);

			JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
			addRequestedExtensions(builder, request.extensions(), false);
			builder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
			builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert.getPublicKey()));
            addCrlDistributionPoint(builder, issuer.getId());
			X509Certificate cert = signAndConvert(builder, issuerSigningKey);

			return new GeneratedCertificate(
					toPem(cert),
					keyPair.getPrivate().getEncoded(),
					serial.toString(16));
		} catch (CertificateGenerationException e) {
			throw e;
		} catch (Exception e) {
			throw new CertificateGenerationException("Failed to generate end-entity certificate", e);
		}
	}

    private void addCrlDistributionPoint(JcaX509v3CertificateBuilder builder, Long issuerCertificateId) throws Exception {
        if (issuerCertificateId == null || crlBaseUrl == null || crlBaseUrl.isBlank()) {
            return;
        }
        String url = crlBaseUrl + "/" + issuerCertificateId + ".crl";
        GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, url);
        DistributionPointName dpn = new DistributionPointName(new GeneralNames(gn));
        DistributionPoint dp = new DistributionPoint(dpn, null, null);
        builder.addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[]{dp}));
    }
	private static X500Name buildX500Name(X500NameRequest r) {
		X500NameBuilder nb = new X500NameBuilder();
		nb.addRDN(BCStyle.CN, r.commonName());
		if (hasValue(r.organization())) {
			nb.addRDN(BCStyle.O, r.organization());
		}
		if (hasValue(r.organizationalUnit())) {
			nb.addRDN(BCStyle.OU, r.organizationalUnit());
		}
		if (hasValue(r.country())) {
			nb.addRDN(BCStyle.C, r.country());
		}
		if (hasValue(r.emailAddress())) {
			nb.addRDN(BCStyle.E, r.emailAddress());
		}
		return nb.build();
	}

	private static boolean hasValue(String s) {
		return s != null && !s.isBlank();
	}

	private static void addRequestedExtensions(
			JcaX509v3CertificateBuilder builder,
			CertificateExtensionsRequest extensions,
			boolean defaultIsCa
	) throws Exception {
		boolean isCa = extensions != null && extensions.isCa() != null ? extensions.isCa() : defaultIsCa;

		BasicConstraints basicConstraints;
		if (isCa) {
			Integer pathLen = extensions != null ? extensions.pathLenConstraint() : null;
			basicConstraints = pathLen != null ? new BasicConstraints(pathLen) : new BasicConstraints(true);
		} else {
			basicConstraints = new BasicConstraints(false);
		}
		builder.addExtension(Extension.basicConstraints, true, basicConstraints);

		KeyUsage keyUsage = buildKeyUsage(extensions != null ? extensions.keyUsages() : null, defaultIsCa);
		builder.addExtension(Extension.keyUsage, true, keyUsage);

		ExtendedKeyUsage eku = buildExtendedKeyUsage(extensions != null ? extensions.extendedKeyUsages() : null);
		if (eku != null) {
			builder.addExtension(Extension.extendedKeyUsage, false, eku);
		}
        GeneralNames san = buildSubjectAlternativeNames(extensions != null ? extensions.subjectAlternativeNames() : null);
        if (san != null) {
            builder.addExtension(Extension.subjectAlternativeName, false, san);
        }
    }

    private static GeneralNames buildSubjectAlternativeNames(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }

        List<GeneralName> names = new ArrayList<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String value = raw.trim();
            int tag = GeneralName.dNSName;
            int sep = value.indexOf(':');
            if (sep > 0) {
                String prefix = value.substring(0, sep).toLowerCase();
                String rest = value.substring(sep + 1).trim();
                if (prefix.equals("ip")) {
                    tag = GeneralName.iPAddress;
                    value = rest;
                } else if (prefix.equals("dns")) {
                    tag = GeneralName.dNSName;
                    value = rest;
                }
            }
            if (!value.isBlank()) {
                names.add(new GeneralName(tag, value));
            }
        }

        if (names.isEmpty()) {
            return null;
        }
        return new GeneralNames(names.toArray(new GeneralName[0]));
	}

	private static KeyUsage buildKeyUsage(Set<CertificateKeyUsage> requested, boolean defaultIsCa) {
		if (requested == null || requested.isEmpty()) {
			if (defaultIsCa) {
				return new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign);
			}
			return new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment);
		}

		int mask = 0;
		for (CertificateKeyUsage ku : requested) {
			if (ku == null) {
				continue;
			}
			mask |= switch (ku) {
				case DIGITAL_SIGNATURE -> KeyUsage.digitalSignature;
				case NON_REPUDIATION -> KeyUsage.nonRepudiation;
				case KEY_ENCIPHERMENT -> KeyUsage.keyEncipherment;
				case DATA_ENCIPHERMENT -> KeyUsage.dataEncipherment;
				case KEY_AGREEMENT -> KeyUsage.keyAgreement;
				case KEY_CERT_SIGN -> KeyUsage.keyCertSign;
				case CRL_SIGN -> KeyUsage.cRLSign;
				case ENCIPHER_ONLY -> KeyUsage.encipherOnly;
				case DECIPHER_ONLY -> KeyUsage.decipherOnly;
			};
		}
		return new KeyUsage(mask);
	}

	private static ExtendedKeyUsage buildExtendedKeyUsage(Set<CertificateExtendedKeyUsage> requested) {
		if (requested == null || requested.isEmpty()) {
			return null;
		}

		KeyPurposeId[] purposes = requested.stream()
				.filter(e -> e != null)
				.map(e -> switch (e) {
					case SERVER_AUTH -> KeyPurposeId.id_kp_serverAuth;
					case CLIENT_AUTH -> KeyPurposeId.id_kp_clientAuth;
					case CODE_SIGNING -> KeyPurposeId.id_kp_codeSigning;
					case EMAIL_PROTECTION -> KeyPurposeId.id_kp_emailProtection;
					case TIME_STAMPING -> KeyPurposeId.id_kp_timeStamping;
					case OCSP_SIGNING -> KeyPurposeId.id_kp_OCSPSigning;
				})
				.distinct()
				.toArray(KeyPurposeId[]::new);

		if (purposes.length == 0) {
			return null;
		}
		return new ExtendedKeyUsage(purposes);
	}

	public GeneratedCertificate generateFromCsr(
			PKCS10CertificationRequest csr,
			Certificate issuer,
			byte[] issuerPrivateKeyPkcs8,
			java.time.Instant notBefore,
			java.time.Instant notAfter
	) {
		try {
			X509Certificate issuerCert = parsePem(issuer.getCertificatePem());
			PrivateKey issuerSigningKey = reconstructRsaPrivateKey(issuerPrivateKeyPkcs8);

			PublicKey subjectPublicKey = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter()
					.setProvider(BC_PROVIDER)
					.getPublicKey(csr.getSubjectPublicKeyInfo());

			boolean signatureValid = csr.isSignatureValid(
					new JcaContentVerifierProviderBuilder().setProvider(BC_PROVIDER).build(subjectPublicKey));
			if (!signatureValid) {
				throw new CertificateGenerationException("CSR signature verification failed.", null);
			}

			X500Name subject = csr.getSubject();
			BigInteger serial = nextSerial();

			JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
					issuerCert,
					serial,
					Date.from(notBefore),
					Date.from(notAfter),
					subject,
					subjectPublicKey
			);

			JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
			builder.addExtension(Extension.keyUsage, true,
					new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
			builder.addExtension(Extension.subjectKeyIdentifier, false,
					extUtils.createSubjectKeyIdentifier(subjectPublicKey));
			builder.addExtension(Extension.authorityKeyIdentifier, false,
					extUtils.createAuthorityKeyIdentifier(issuerCert.getPublicKey()));

			X509Certificate cert = signAndConvert(builder, issuerSigningKey);

			return new GeneratedCertificate(toPem(cert), null, serial.toString(16));
		} catch (CertificateGenerationException e) {
			throw e;
		} catch (Exception e) {
			throw new CertificateGenerationException("Failed to generate certificate from CSR.", e);
		}
	}

	KeyPair generateRsaKeyPair(int keySize) throws Exception {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", BC_PROVIDER);
		keyGen.initialize(keySize, secureRandom);
		return keyGen.generateKeyPair();
	}

	private BigInteger nextSerial() {
		return new BigInteger(SERIAL_BIT_LENGTH, secureRandom).abs();
	}

	private X509Certificate signAndConvert(JcaX509v3CertificateBuilder builder, PrivateKey signingKey) throws Exception {
		ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
				.setProvider(BC_PROVIDER)
				.build(signingKey);
		X509CertificateHolder holder = builder.build(signer);
		return new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(holder);
	}

	private static String toPem(X509Certificate certificate) throws Exception {
		StringWriter sw = new StringWriter();
		try (PemWriter pw = new PemWriter(sw)) {
			pw.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
		}
		return sw.toString();
	}

	private static X509Certificate parsePem(String pem) {
		if (pem == null || pem.isBlank()) {
			throw new CertificateGenerationException("Issuer certificate PEM is empty.", null);
		}
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509", BC_PROVIDER);
			return (X509Certificate) cf.generateCertificate(
					new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new CertificateGenerationException("Failed to parse issuer certificate PEM.", e);
		}
	}

	private static PrivateKey reconstructRsaPrivateKey(byte[] pkcs8) {
		if (pkcs8 == null || pkcs8.length == 0) {
			throw new CertificateGenerationException("Issuer private key is empty.", null);
		}
		try {
			KeyFactory kf = KeyFactory.getInstance("RSA", BC_PROVIDER);
			return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		} catch (Exception e) {
			throw new CertificateGenerationException("Failed to reconstruct issuer RSA private key.", e);
		}
	}
}
