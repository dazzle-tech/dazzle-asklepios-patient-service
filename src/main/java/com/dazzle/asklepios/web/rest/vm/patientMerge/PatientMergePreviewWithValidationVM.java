package com.dazzle.asklepios.web.rest.vm.patientMerge;

import java.util.List;

public record PatientMergePreviewWithValidationVM(

        boolean valid,

        List<ValidationIssueVM> issues,

        PatientMergePreviewVM preview

) {}