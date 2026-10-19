package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record RegistrationPendingPaymentEncounterDTO(
        Long encounterId,
        String encounterNumber,
        Long patientId,
        String patientName,
        Map<String, Object> patient,
        Long bedId,
        String bedName,
        int pendingItemCount,
        boolean collectible,
        List<RegistrationPendingPaymentItemDTO> items
) implements Serializable {
}
