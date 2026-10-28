package com.dazzle.asklepios.service.dto.patientComplaint;

import com.dazzle.asklepios.domain.enumeration.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatientComplaintPublicLookupRequestDTO(
    @NotNull(message = "Document type is required") DocumentType documentType,
    @NotBlank(message = "Document number is required") String documentNumber
) {}
