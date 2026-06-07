package com.ib.dto.certificate;

import com.ib.domain.enums.RevocationReason;
import jakarta.validation.constraints.NotNull;

public record RevokeCertificateRequest(
        @NotNull RevocationReason reason
) {
}
