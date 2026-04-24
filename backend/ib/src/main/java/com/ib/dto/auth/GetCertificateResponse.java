package com.ib.dto.auth;

import com.ib.domain.entity.Certificate;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import com.ib.domain.enums.RevocationReason;

import java.time.Instant;

public record GetCertificateResponse(
        Long id,
        String serialNumber,
        CertificateType type,
        CertificateStatus status,
        String subjectCommonName,
        String organizationName,
        String issuerSerialNumber,
        Instant notBefore,
        Instant notAfter,
        Instant revokedAt,
        RevocationReason revocationReason
) {
    public GetCertificateResponse(Certificate certificate){
        this(certificate.getId(),
                certificate.getSerialNumber(),
                certificate.getType(),
                certificate.getStatus(),
                certificate.getSubject().getCommonName(),
                certificate.getOrganization().getName(),
                certificate.getIssuer() != null ? certificate.getIssuer().getSerialNumber() : null,
                certificate.getNotBefore(),
                certificate.getNotAfter(),
                certificate.getRevokedAt(),
                certificate.getRevocationReason());
    }
}
