package com.dazzle.asklepios.service.dto.dentalProcedure;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DentalProcedureCancellationDTO {

    @NotBlank(message = "Cancellation reason is required.")
    private String cancellationReason;
}