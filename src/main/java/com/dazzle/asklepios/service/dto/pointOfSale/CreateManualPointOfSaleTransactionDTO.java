package com.dazzle.asklepios.service.dto.pointOfSale;

import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleSourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;

public record CreateManualPointOfSaleTransactionDTO(

        @NotNull
        Long patientId,

        @NotNull
        PointOfSaleSourceType sourceType,

        @NotNull
        Long sourceReferenceId,

        @NotNull
        @DecimalMin("0.0001")
        BigDecimal amount,

        @NotBlank
        String panNumber,

        @NotBlank
        String transactionId

) implements Serializable {
}
