package com.dazzle.asklepios.service.dto.bedTransaction;

import com.dazzle.asklepios.domain.enumeration.BedTransactionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BedTransactionUpdateDTO(

        @NotNull
        Long id,

        @NotNull
        Long encounterId,

        @NotNull
        Long patientId,

        Long fromRoomId,

        Long fromBedId,

        Long toRoomId,

        Long toBedId,

        @NotNull
        Long fromDepartmentId,

        @NotNull
        Long toDepartmentId,

        @NotNull
        Boolean isExternal,

        @NotNull
        BedTransactionType transactionType

) implements Serializable {
}