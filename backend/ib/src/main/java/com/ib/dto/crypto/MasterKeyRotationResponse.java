package com.ib.dto.crypto;

import com.ib.domain.enums.MasterKeyRotationJobStatus;

public record MasterKeyRotationResponse(
		Long jobId,
		MasterKeyRotationJobStatus status
) {
}
