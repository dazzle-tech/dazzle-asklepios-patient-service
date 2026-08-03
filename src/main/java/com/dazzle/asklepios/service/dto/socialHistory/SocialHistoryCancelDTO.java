package com.dazzle.asklepios.service.dto.socialHistory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialHistoryCancelDTO(

        @NotNull
        Long id,

        @NotBlank
        String cancellationReason

) {
}