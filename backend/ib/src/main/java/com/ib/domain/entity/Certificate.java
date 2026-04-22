package com.ib.domain.entity;

import com.ib.domain.embeddable.X509SubjectFields;
import com.ib.domain.enums.CertificateStatus;
import com.ib.domain.enums.CertificateType;
import com.ib.domain.enums.RevocationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Certificate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	@ToString.Include
	private Long id;

	@Column(name = "serial_number", nullable = false, unique = true, length = 128)
	private String serialNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CertificateType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	@Builder.Default
	private CertificateStatus status = CertificateStatus.ACTIVE;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "organization_id", nullable = false)
	@ToString.Exclude
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_user_id")
	@ToString.Exclude
	private User owner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "issuer_certificate_id")
	@ToString.Exclude
	private Certificate issuer;

	@Embedded
	private X509SubjectFields subject;

	@Column(name = "not_before", nullable = false)
	private Instant notBefore;

	@Column(name = "not_after", nullable = false)
	private Instant notAfter;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "revocation_reason", length = 32)
	private RevocationReason revocationReason;

	@Column(name = "certificate_pem", columnDefinition = "TEXT")
	private String certificatePem;
}
