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
    public static ExternalTestResponseVM ofEntity(ExternalTest externalTest) {
        return new ExternalTestResponseVM(
                externalTest.getId(),
                externalTest.getTestId(),
                externalTest.getFacilityName(),
                externalTest.getReason(),
                externalTest.getCreatedBy(),
                externalTest.getCreatedDate()
        );
    }
}

