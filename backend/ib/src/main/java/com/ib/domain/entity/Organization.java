package com.ib.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Organization {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	@ToString.Include
	private Long id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(name = "encrypted_org_key")
	@ToString.Exclude
	private byte[] encryptedOrgKey;

	@Column(name = "org_key_nonce")
	@ToString.Exclude
	private byte[] orgKeyNonce;

	@Column(name = "org_key_version")
	private Integer orgKeyVersion;

	@OneToMany(mappedBy = "organization")
	@Builder.Default
	@ToString.Exclude
	private Set<User> users = new HashSet<>();

	@OneToMany(mappedBy = "organization")
	@Builder.Default
	@ToString.Exclude
	private Set<Certificate> certificates = new HashSet<>();
}
