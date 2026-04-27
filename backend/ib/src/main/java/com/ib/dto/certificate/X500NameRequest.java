package com.ib.dto.certificate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record X500NameRequest(
		@NotBlank @Size(max = 255) String commonName,
		@Size(max = 255) String organization,
		@Size(max = 255) String organizationalUnit,
		@Size(min = 2, max = 2) String country,
		@Email @Size(max = 255) String emailAddress
) {
}
