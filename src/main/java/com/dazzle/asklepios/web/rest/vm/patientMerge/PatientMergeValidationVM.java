package com.dazzle.asklepios.web.rest.vm.patientMerge;

import java.io.Serializable;
import java.util.List;

public record PatientMergeValidationVM(

        boolean valid,

        List<ValidationIssueVM> issues

) implements Serializable {}
