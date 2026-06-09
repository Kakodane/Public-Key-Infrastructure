package com.ib.domain.entity;

import com.ib.domain.enums.MasterKeyRotationJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "master_key_rotation_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class MasterKeyRotationJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	@ToString.Include
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MasterKeyRotationJobStatus status;

	@Column(name = "from_version", nullable = false)
	private int fromVersion;

	@Column(name = "to_version", nullable = false)
	private int toVersion;

	@Column(name = "orgs_processed", nullable = false)
	@Builder.Default
	private int orgsProcessed = 0;

	@Column(name = "users_processed", nullable = false)
	@Builder.Default
	private int usersProcessed = 0;

	@Column(name = "total_orgs", nullable = false)
	@Builder.Default
	private int totalOrgs = 0;

	@Column(name = "total_users", nullable = false)
	@Builder.Default
	private int totalUsers = 0;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;
}
