package com.ib.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank
        @Size(min = 2, max = 128)
        String name
) {
}
