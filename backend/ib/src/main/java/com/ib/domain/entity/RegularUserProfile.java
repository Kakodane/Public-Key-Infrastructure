package com.ib.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Role-specific data for {@link Role#REGULAR_USER}. Shares primary key with {@link User}.
 */
@Entity
@Table(name = "regular_user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class RegularUserProfile {

	@Id
	@EqualsAndHashCode.Include
	@ToString.Include
	private Long id;

	@OneToOne(optional = false, fetch = FetchType.LAZY)
	@MapsId
	@JoinColumn(name = "user_id")
	@ToString.Exclude
	private User user;

	@Column(name = "registration_public_key", columnDefinition = "TEXT")
	private String registrationPublicKey;
}
