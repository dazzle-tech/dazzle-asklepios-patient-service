package com.dazzle.asklepios.integration.waseel.dto.approval;

public record SupportingInfoDTO(
        Integer sequence,
        String category,
        String code,
        String fromDate,
        String toDate,
        String value,
        String reason,
        String attachment,
        String attachmentName,
        String attachmentType,
        String unit,
        String attachmentDate
) {}