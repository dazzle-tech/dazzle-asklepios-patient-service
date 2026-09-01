package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;

public record EncounterHasInvoiceResponse(
        Long encounterId,
        boolean hasInvoice
) implements Serializable {
}
