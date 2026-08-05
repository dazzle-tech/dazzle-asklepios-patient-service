package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record PrepareDefaultServiceItem(

        @NotNull
        Long serviceId,

        @NotNull
        @Min(1)
        Long quantity,

        @NotNull
        @Min(1)
        Integer sequence,

        Boolean isExempted

) implements Serializable {

    public boolean exempted() {
        return Boolean.TRUE.equals(isExempted);
    }
}