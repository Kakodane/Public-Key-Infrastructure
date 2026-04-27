package com.ib.service.certificate;

import com.ib.domain.entity.Certificate;
import com.ib.dto.certificate.CreateEndEntityCertificateRequest;
import com.ib.dto.certificate.CreateIntermediateCertificateRequest;
import com.ib.dto.certificate.CreateRootCertificateRequest;
import com.ib.dto.certificate.X500NameRequest;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;


@Service
public class CertificateGenerationService {

	private static final int ROOT_KEY_SIZE = 4096;
	private static final int INTERMEDIATE_KEY_SIZE = 3072;
	private static final int END_ENTITY_KEY_SIZE = 2048;

	private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";
	private static final String BC_PROVIDER = "BC";
	private static final int SERIAL_BIT_LENGTH = 128;

	private final SecureRandom secureRandom = new SecureRandom();

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
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
			builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
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
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
			builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
			builder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
			builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert.getPublicKey()));

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
			builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
			builder.addExtension(Extension.keyUsage, true,
					new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
			builder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
			builder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert.getPublicKey()));

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

	private KeyPair generateRsaKeyPair(int keySize) throws Exception {
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
