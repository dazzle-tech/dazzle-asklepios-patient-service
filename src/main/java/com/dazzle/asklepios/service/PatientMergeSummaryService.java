package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeAutoTransferDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeDecisionDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryItemDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryRequest;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryResponse;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergePreviewResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientMergeSummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientMergeSummaryService.class);

    private final PatientMergeAnalysisService analysisService;

    public PatientMergeSummaryService(PatientMergeAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    public PatientMergeSummaryResponse summarizeMerge(PatientMergeSummaryRequest request) {
        LOG.debug("Summarizing merge from patient {} to patient {}", request.getFromPatientId(), request.getToPatientId());
        validateRequest(request);

        // Get backend-calculated conflicts and autoTransfers (ignore frontend autoTransfers)
        PatientMergePreviewResponse analysis = analysisService.analyze(request.getFromPatientId(), request.getToPatientId());

        List<PatientMergeSummaryItemDTO> fieldUpdates = new ArrayList<>();
        List<PatientMergeSummaryItemDTO> autoTransfers = new ArrayList<>();
        List<PatientMergeSummaryItemDTO> recordsToAdd = new ArrayList<>();
        List<PatientMergeSummaryItemDTO> ignoredItems = new ArrayList<>();

        // Process user decisions
        if (request.getDecisions() != null) {
            for (PatientMergeDecisionDTO decision : request.getDecisions()) {
                if (decision.getFinalDecision() == null) {
                    continue;
                }

                if (decision.getFinalDecision() == MergeDecision.TAKE_FROM
                        || decision.getFinalDecision() == MergeDecision.MANUAL) {

                    fieldUpdates.add(toSummaryItem(
                            decision,
                            decision.getToValue(),
                            resolveNewValue(decision)
                    ));
                }

                if (decision.getFinalDecision() == MergeDecision.ADD_FROM_RECORD) {
                    recordsToAdd.add(toSummaryItem(
                            decision,
                            "",
                            decision.getSelectedValue()
                    ));
                }

                if (decision.getFinalDecision() == MergeDecision.IGNORE_FROM_RECORD
                        || decision.getFinalDecision() == MergeDecision.KEEP_TO) {

                    ignoredItems.add(toSummaryItem(
                            decision,
                            decision.getToValue(),
                            decision.getToValue()
                    ));
                }
            }
        }

        // Process backend-calculated autoTransfers (not frontend)
        if (analysis.getAutoTransfers() != null) {
            for (PatientMergeAutoTransferDTO autoTransfer : analysis.getAutoTransfers()) {
                autoTransfers.add(toSummaryItem(autoTransfer));
            }
        }

        return PatientMergeSummaryResponse.builder()
                .fromPatientId(request.getFromPatientId())
                .toPatientId(request.getToPatientId())
                .fieldUpdates(fieldUpdates)
                .autoTransfers(autoTransfers)
                .recordsToAdd(recordsToAdd)
                .ignoredItems(ignoredItems)
                .build();
    }

    private String resolveNewValue(PatientMergeDecisionDTO decision) {
        if (decision.getFinalDecision() == MergeDecision.TAKE_FROM) {
            return decision.getFromValue();
        }

        if (decision.getFinalDecision() == MergeDecision.MANUAL) {
            return decision.getSelectedValue();
        }

        return decision.getSelectedValue();
    }

    private PatientMergeSummaryItemDTO toSummaryItem(
            PatientMergeDecisionDTO decision,
            String oldValue,
            String newValue
    ) {
        return PatientMergeSummaryItemDTO.builder()
                .entityName(decision.getEntityName())
                .tableName(decision.getTableName())
                .fromRecordId(decision.getFromRecordId())
                .toRecordId(decision.getToRecordId())
                .matchKey(decision.getMatchKey())
                .fieldName(decision.getFieldName())
                .fieldLabel(decision.getFieldLabel())
                .oldValue(oldValue)
                .newValue(newValue)
                .decision(decision.getFinalDecision())
                .build();
    }

    private PatientMergeSummaryItemDTO toSummaryItem(PatientMergeAutoTransferDTO autoTransfer) {
        return PatientMergeSummaryItemDTO.builder()
                .entityName(autoTransfer.getEntityName())
                .tableName(autoTransfer.getTableName())
                .fromRecordId(autoTransfer.getFromRecordId())
                .toRecordId(autoTransfer.getToRecordId())
                .matchKey(autoTransfer.getMatchKey())
                .fieldName(autoTransfer.getFieldName())
                .fieldLabel(autoTransfer.getFieldLabel())
                .oldValue(autoTransfer.getToValue())
                .newValue(autoTransfer.getSelectedValue())
                .decision(autoTransfer.getSuggestedDecision())
                .build();
    }

    private void validateRequest(PatientMergeSummaryRequest request) {
        if (request == null) {
            throw new BadRequestAlertException("Request is required", "PatientMerge", "request.required");
        }

        if (request.getFromPatientId() == null || request.getToPatientId() == null) {
            throw new BadRequestAlertException("Patient IDs are required", "PatientMerge", "ids.required");
        }

        if (request.getFromPatientId().equals(request.getToPatientId())) {
            throw new BadRequestAlertException("Cannot merge same patient", "PatientMerge", "same.patient");
        }
    }
}