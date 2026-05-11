package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergePreviewResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientMergeService {

    private final PatientMergeAnalysisService patientMergeAnalysisService;

    public PatientMergeService(PatientMergeAnalysisService patientMergeAnalysisService) {
        this.patientMergeAnalysisService = patientMergeAnalysisService;
    }

    public PatientMergePreviewResponse previewMerge(Long fromPatientId, Long toPatientId) {
        return patientMergeAnalysisService.analyze(fromPatientId, toPatientId);
    }


}