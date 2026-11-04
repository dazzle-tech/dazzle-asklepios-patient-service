package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.util.List;

public record RegistrationPendingPaymentChangesDTO(
        Long afterId,
        Long latestId,
        long totalCount,
        List<Long> newItemIds
) implements Serializable {
}
