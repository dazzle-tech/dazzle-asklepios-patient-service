package com.dazzle.asklepios.service.dailybriefing;

import java.util.List;

public record BriefingScope(
        Long facilityId,
        Long departmentId,
        Long practitionerId,
        List<Long> practitionerIds,
        boolean matchNone
) {
}
