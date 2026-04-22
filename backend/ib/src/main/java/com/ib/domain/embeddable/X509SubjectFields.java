package com.ib.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Subject distinguished name fields commonly used when issuing certificates.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class X509SubjectFields {

	@Column(name = "subject_cn")
	private String commonName;

	@Column(name = "subject_o")
	private String organization;

	@Column(name = "subject_ou")
	private String organizationalUnit;

	@Column(name = "subject_c", length = 2)
	private String country;

	@Column(name = "subject_email")
	private String emailAddress;
}
