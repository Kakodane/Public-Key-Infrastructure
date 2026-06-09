package com.ib.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "crypto")
@Validated
public record CryptoProperties(
		@NotBlank String masterKeyBase64,
		boolean masterKeyRotationEnabled,
		@NotBlank String masterKeyRotationCron,
		int masterKeyRotationBatchSize
) {
}
