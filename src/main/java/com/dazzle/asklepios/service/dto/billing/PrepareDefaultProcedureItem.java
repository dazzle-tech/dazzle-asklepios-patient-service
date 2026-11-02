package com.dazzle.asklepios.service.dto.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * A default procedure of the visit department/specialty. Each one becomes a
 * procedure order of the encounter with a single billed unit.
 */
public record PrepareDefaultProcedureItem(

        @NotNull
        Long procedureId,

        @NotNull
        @Min(1)
        Integer sequence,

        Boolean isExempted

) implements Serializable {

    public static final long QUANTITY = 1L;

    public boolean exempted() {
        return Boolean.TRUE.equals(isExempted);
    }
}
