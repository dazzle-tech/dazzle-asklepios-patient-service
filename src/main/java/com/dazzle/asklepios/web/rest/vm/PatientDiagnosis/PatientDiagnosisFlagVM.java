package com.dazzle.asklepios.web.rest.vm.PatientDiagnosis;

public record PatientDiagnosisFlagVM(

        Long encounterId,
        Boolean hasPrimaryDiagnoses


) {

    public static PatientDiagnosisFlagVM of(Long encounterId, Boolean hasPrimaryDiagnoses) {
        return new PatientDiagnosisFlagVM(encounterId, hasPrimaryDiagnoses);
    }
}