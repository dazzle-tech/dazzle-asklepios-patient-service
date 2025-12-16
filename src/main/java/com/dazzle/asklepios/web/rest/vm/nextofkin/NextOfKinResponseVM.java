package com.dazzle.asklepios.web.rest.vm.nextofkin;

import com.dazzle.asklepios.domain.NextOfKin;
import com.dazzle.asklepios.domain.enumeration.RelationType;

import java.io.Serializable;

/**
 * View Model for reading Next of Kin via REST.
 */
public record NextOfKinResponseVM(
        Long id,
        Long patientId,
        String name,
        RelationType relationship,
        String address,
        String email,
        String mobileNumber,
        String telephone,
        String internationalNumber,
        String landlineNumber
) implements Serializable {

    public static NextOfKinResponseVM ofEntity(NextOfKin nok) {
        return new NextOfKinResponseVM(
                nok.getId(),
                nok.getPatient() != null ? nok.getPatient().getId() : null,
                nok.getName(),
                nok.getRelationship(),
                nok.getAddress(),
                nok.getEmail(),
                nok.getMobileNumber(),
                nok.getTelephone(),
                nok.getInternationalNumber(),
                nok.getLandlineNumber()
        );
    }
}
