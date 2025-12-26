package com.dazzle.asklepios.web.rest.vm.reviewofsystem;

import com.dazzle.asklepios.domain.ReviewOfSystem;

import java.io.Serializable;

public record ReviewOfSystemResponseVM(
        Long id,
        Long patientId,
        Long encounterId,
        String bodySystem,
        String systemDetail,
        String note
) implements Serializable {

    public static ReviewOfSystemResponseVM ofEntity(ReviewOfSystem ros) {
        return new ReviewOfSystemResponseVM(
                ros.getId(),
                ros.getPatientId(),
                ros.getEncounterId(),
                ros.getBodySystem(),
                ros.getSystemDetail(),
                ros.getNote()
        );
    }
}

