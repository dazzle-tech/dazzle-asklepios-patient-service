package com.dazzle.asklepios.client.gateway.dto;

public record PatientCreatePasswordMailDTO(
        String language,
        Long patientId,
        String name,
        String documentId,
        String email,
        String token
) {
}
