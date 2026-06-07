package com.ib.service.certificate;

import com.ib.crypto.CertificatePrivateKeyCipher;
import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.PublishedCrl;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import com.ib.domain.enums.RevocationReason;
import com.ib.domain.repository.CertificateRepository;
import com.ib.domain.repository.PublishedCrlRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class CrlService {

    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";
    private static final String BC_PROVIDER = "BC";

    private final CertificateRepository certificateRepository;
    private final PublishedCrlRepository publishedCrlRepository;
    private final CertificatePrivateKeyCipher privateKeyCipher;

    @Value("${app.crl.next-update-days:1}")
    private long nextUpdateDays;


    @Transactional
    public byte[] serveCrl(Long caCertificateId) {
        PublishedCrl published = publishedCrlRepository.findByCaCertificate_Id(caCertificateId)
                .orElse(null);
        if (published == null) {
            published = buildAndStore(caCertificateId);
        }
        return published.getData();
    }

    @Transactional
    public void regenerateForCa(Long caCertificateId) {
        buildAndStore(caCertificateId);
    }

    private PublishedCrl buildAndStore(Long caCertificateId) {
        Certificate ca = certificateRepository.findById(caCertificateId)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + caCertificateId));
        if (ca.getType() == CertificateType.END_ENTITY) {
            throw new CertificateIssuanceException("End-entity certificates do not publish a CRL.");
        }

        PublishedCrl existing = publishedCrlRepository.findByCaCertificate_Id(caCertificateId).orElse(null);
        long crlNumber = existing != null ? existing.getCrlNumber() + 1 : 1L;
        Instant thisUpdate = Instant.now();
        Instant nextUpdate = thisUpdate.plus(nextUpdateDays, ChronoUnit.DAYS);

        byte[] der = buildSignedCrl(ca, thisUpdate, nextUpdate, crlNumber);

        PublishedCrl target = existing != null ? existing : PublishedCrl.builder().caCertificate(ca).build();
        target.setCrlNumber(crlNumber);
        target.setThisUpdate(thisUpdate);
        target.setNextUpdate(nextUpdate);
        target.setData(der);
        return publishedCrlRepository.save(target);
    }

    private byte[] buildSignedCrl(Certificate ca, Instant thisUpdate, Instant nextUpdate, long crlNumber) {
        try {
            X509Certificate caCert = parsePem(ca.getCertificatePem());
            PrivateKey caKey = reconstructRsaPrivateKey(privateKeyCipher.loadPrivateKey(ca.getId()));

            X500Name issuerName = new JcaX509CertificateHolder(caCert).getSubject();
            X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuerName, Date.from(thisUpdate));
            crlBuilder.setNextUpdate(Date.from(nextUpdate));

            for (Certificate issued : certificateRepository.findAllByIssuer_Id(ca.getId())) {
                if (issued.getStatus() != CertificateStatus.REVOKED) {
                    continue;
                }
                if (issued.getId() != null && issued.getId().equals(ca.getId())) {
                    continue;
                }
                BigInteger serial = new BigInteger(issued.getSerialNumber(), 16);
                Date revokedAt = issued.getRevokedAt() != null ? Date.from(issued.getRevokedAt()) : Date.from(thisUpdate);
                crlBuilder.addCRLEntry(serial, revokedAt, toCrlReasonCode(issued.getRevocationReason()));
            }

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            crlBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(caCert.getPublicKey()));
            crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.valueOf(crlNumber)));

            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider(BC_PROVIDER)
                    .build(caKey);
            X509CRLHolder crlHolder = crlBuilder.build(signer);
            return crlHolder.getEncoded();
        } catch (CertificateIssuanceException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateGenerationException("Failed to generate CRL", e);
        }
    }

    private static int toCrlReasonCode(RevocationReason reason) {
        if (reason == null) {
            return CRLReason.unspecified;
        }
        return switch (reason) {
            case UNSPECIFIED -> CRLReason.unspecified;
            case KEY_COMPROMISE -> CRLReason.keyCompromise;
            case CA_COMPROMISE -> CRLReason.cACompromise;
            case AFFILIATION_CHANGED -> CRLReason.affiliationChanged;
            case SUPERSEDED -> CRLReason.superseded;
            case CESSATION_OF_OPERATION -> CRLReason.cessationOfOperation;
            case CERTIFICATE_HOLD -> CRLReason.certificateHold;
            case REMOVE_FROM_CRL -> CRLReason.removeFromCRL;
            case PRIVILEGE_WITHDRAWN -> CRLReason.privilegeWithdrawn;
            case AA_COMPROMISE -> CRLReason.aACompromise;
        };
    }

    private static X509Certificate parsePem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new CertificateGenerationException("CA certificate PEM is empty.", null);
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509", BC_PROVIDER);
            return (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CertificateGenerationException("Failed to parse CA certificate PEM.", e);
        }
    }

    private static PrivateKey reconstructRsaPrivateKey(byte[] pkcs8) {
        if (pkcs8 == null || pkcs8.length == 0) {
            throw new CertificateGenerationException("CA private key is empty.", null);
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA", BC_PROVIDER);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new CertificateGenerationException("Failed to reconstruct CA RSA private key.", e);
        }
    }
}