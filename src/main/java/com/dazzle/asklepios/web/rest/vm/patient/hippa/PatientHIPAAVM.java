package com.dazzle.asklepios.web.rest.vm.patient.hippa;


import com.dazzle.asklepios.domain.PatientHIPAA;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientHIPAAVM(
        Long patientId,
        Boolean noticeOfPrivacyPractice,
        Boolean privacyAuthorization,
        LocalDate noticeOfPrivacyPracticeDate,
        LocalDate privacyAuthorizationDate
) implements Serializable {

    public static PatientHIPAAVM ofEntity(PatientHIPAA e) {
        return new PatientHIPAAVM(
                e.getPatientId(),
                e.getNoticeOfPrivacyPractice(),
                e.getPrivacyAuthorization(),
                e.getNoticeOfPrivacyPracticeDate(),
                e.getPrivacyAuthorizationDate()
        );
    }
}
