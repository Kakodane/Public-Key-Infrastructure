package com.ib.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
@Validated
public record JwtProperties(
		@NotBlank String secret,
		Duration accessTtl,
		Duration refreshTtl
) {
}
