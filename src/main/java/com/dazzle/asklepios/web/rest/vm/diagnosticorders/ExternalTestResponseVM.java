package com.dazzle.asklepios.web.rest.vm.diagnosticorders;
import com.dazzle.asklepios.domain.ExternalTest;
import java.time.Instant;
public record ExternalTestResponseVM(
        Long id,
        Long testId,
        String facilityName,
        String reason,
        String createdBy,
        Instant createdDate
) {
    public static ExternalTestResponseVM ofEntity(ExternalTest e) {
        return new ExternalTestResponseVM(
                e.getId(),
                e.getTestId(),
                e.getFacilityName(),
                e.getReason(),
                e.getCreatedBy(),
                e.getCreatedDate()
        );
    }
}

