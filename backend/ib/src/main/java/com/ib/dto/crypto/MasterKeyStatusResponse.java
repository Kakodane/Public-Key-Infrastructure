package com.ib.dto.crypto;

import com.ib.domain.enums.MasterKeyRotationJobStatus;

import java.time.Instant;

public record MasterKeyStatusResponse(
		int currentVersion,
		Instant createdAt,
		Instant lastRotationAt,
		MasterKeyRotationJobStatus jobStatus
) {
}
