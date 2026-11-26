package com.dazzle.asklepios.service.dto.survey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Submitted survey. The patient id is never sent: the app sends the patient's login token
 * (Authorization header), the web page sends verificationToken from its OTP.
 * Without either, the survey is saved without a patient.
 */
public record SurveySubmitDTO(
        String verificationToken,
        /** From the patient's device; ignored when it is after the server time */
        Instant startedAt,
        @NotNull @Valid List<SurveyAnswerDTO> answers
) {
}
