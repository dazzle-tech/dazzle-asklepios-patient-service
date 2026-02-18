package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;


public record RejectResultDTO(
        Long resultId,
        String rejectedBy,
        String rejectedReason
) {}
