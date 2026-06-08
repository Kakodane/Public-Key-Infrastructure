package com.ib.service.certificate;

import com.ib.crypto.CertificatePrivateKeyCipher;
import com.ib.domain.entity.Certificate;
import com.ib.domain.embeddable.X509SubjectFields;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificateKeystoreExportService {

	private static final String BC_PROVIDER = "BC";
	private static final String PKCS12_TYPE = "PKCS12";

	private final CertificatePrivateKeyCipher privateKeyCipher;

	public byte[] exportEphemeralKeystore(
			byte[] pkcs8PrivateKey,
			Certificate leaf,
			String format,
			char[] keystorePassword
	) {
		if (pkcs8PrivateKey == null || pkcs8PrivateKey.length == 0) {
			throw new IllegalArgumentException("Private key must not be empty");
		}
		if (leaf == null) {
			throw new IllegalArgumentException("Certificate must not be null");
		}
		if (keystorePassword == null || keystorePassword.length == 0) {
			throw new IllegalArgumentException("Keystore password must not be empty");
		}

		String keystoreType = "jks".equalsIgnoreCase(format) ? "JKS" : PKCS12_TYPE;

		try {
			PrivateKey privateKey = reconstructRsaPrivateKey(pkcs8PrivateKey);
			java.security.cert.Certificate[] chain = buildChain(leaf);

			KeyStore keyStore = KeyStore.getInstance(keystoreType);
			keyStore.load(null, null);
			keyStore.setKeyEntry(resolveAlias(leaf), privateKey, keystorePassword, chain);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			keyStore.store(out, keystorePassword);
			return out.toByteArray();
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to export " + keystoreType + " keystore", e);
		}
	}

	public byte[] exportPkcs12(Certificate leaf, char[] keystorePassword) {
		if (leaf == null) {
			throw new IllegalArgumentException("certificate must not be null");
		}
		if (keystorePassword == null || keystorePassword.length == 0) {
			throw new IllegalArgumentException("keystore password must not be empty");
		}
		if (leaf.getEncryptedPrivateKey() == null) {
			throw new IllegalArgumentException("Private key is not available for download");
		}

		try {
			X509Certificate leafCert = parsePem(leaf.getCertificatePem());
			byte[] pkcs8 = privateKeyCipher.loadPrivateKey(leaf.getId());
			PrivateKey privateKey = reconstructRsaPrivateKey(pkcs8);
			java.security.cert.Certificate[] chain = buildChain(leaf);

			KeyStore keyStore = KeyStore.getInstance(PKCS12_TYPE);
			keyStore.load(null, null);
			keyStore.setKeyEntry(resolveAlias(leaf), privateKey, keystorePassword, chain);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			keyStore.store(out, keystorePassword);
			return out.toByteArray();
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to export PKCS#12 keystore", e);
		}
	}

	private static java.security.cert.Certificate[] buildChain(Certificate leaf) {
		List<X509Certificate> certificates = new ArrayList<>();
		certificates.add(parsePem(leaf.getCertificatePem()));

		Certificate issuer = leaf.getIssuer();
		while (issuer != null) {
			certificates.add(parsePem(issuer.getCertificatePem()));
			issuer = issuer.getIssuer();
		}

		return certificates.toArray(java.security.cert.Certificate[]::new);
	}

	private static String resolveAlias(Certificate leaf) {
		X509SubjectFields subject = leaf.getSubject();
		if (subject != null && subject.getCommonName() != null && !subject.getCommonName().isBlank()) {
			return subject.getCommonName();
		}
		return leaf.getSerialNumber();
	}

	private static X509Certificate parsePem(String pem) {
		if (pem == null || pem.isBlank()) {
			throw new IllegalArgumentException("Certificate PEM is empty");
		}
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509", BC_PROVIDER);
			return (X509Certificate) cf.generateCertificate(
					new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to parse certificate PEM", e);
		}
	}

	private static PrivateKey reconstructRsaPrivateKey(byte[] pkcs8) {
		if (pkcs8 == null || pkcs8.length == 0) {
			throw new IllegalArgumentException("Private key is empty");
		}
		try {
			KeyFactory kf = KeyFactory.getInstance("RSA", BC_PROVIDER);
			return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to reconstruct RSA private key", e);
		}
	}
}
