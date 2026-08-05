package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSubscriber;
import org.springframework.stereotype.Component;

@Component
public class ApprovalSubscriberMapper {

    public WaseelApprovalSubscriber toSubscriber(Patient patient) {
        if (patient == null) {
            return null;
        }

        return new WaseelApprovalSubscriber(
                patient.getFirstName(),
                patient.getSecondName(),
                patient.getThirdName(),
                patient.getLastName(),
                buildFullName(patient),
                patient.getId() == null ? null : patient.getId().toString(),
                patient.getDateOfBirth() == null ? null : patient.getDateOfBirth().toString(),
                patient.getSexAtBirth() == null ? null : patient.getSexAtBirth().name(),
                null,
                patient.getDocumentId(),
                null,
                patient.getNationality(),
                null,
                patient.getPrimaryMobileNumber(),
                patient.getMaritalStatus(),
                null,
                null,
                patient.getPreferredLanguage(),
                patient.getEmergencyContactPhone(),
                patient.getEmail(),
                null,
                null,
                null,
                null,
                null,
                null,
                patient.getReligion()
        );
    }

    private String buildFullName(Patient patient) {
        StringBuilder fullName = new StringBuilder();

        append(fullName, patient.getFirstName());
        append(fullName, patient.getSecondName());
        append(fullName, patient.getThirdName());
        append(fullName, patient.getLastName());

        return fullName.isEmpty() ? null : fullName.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append(" ");
        }

        builder.append(value.trim());
    }
}