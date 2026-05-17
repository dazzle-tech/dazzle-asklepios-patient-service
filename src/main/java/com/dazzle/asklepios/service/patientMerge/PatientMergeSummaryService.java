package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeAutoTransferDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeConflictDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeDecisionDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryItemDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergePreviewVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeSummaryVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientMergeSummaryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeSummaryService.class);

    private final PatientMergeAnalysisService analysisService;

    public PatientMergeSummaryService(
            PatientMergeAnalysisService analysisService
    ) {
        this.analysisService = analysisService;
    }

    public PatientMergeSummaryVM summarizeMerge(
            PatientMergeSummaryDTO request
    ) {
        LOG.debug(
                "Summarizing patient merge. fromPatientId={}, toPatientId={}",
                request != null ? request.getFromPatientId() : null,
                request != null ? request.getToPatientId() : null
        );

        validateRequest(request);

        PatientMergePreviewVM analysis = analysisService.analyze(
                request.getFromPatientId(),
                request.getToPatientId()
        );

        List<PatientMergeSummaryItemDTO> fieldUpdates = new ArrayList<>();
        List<PatientMergeSummaryItemDTO> autoTransfers = new ArrayList<>();
        List<PatientMergeSummaryItemDTO> recordsToAdd = new ArrayList<>();
        List<PatientMergeSummaryItemDTO> ignoredItems = new ArrayList<>();

        processUserDecisions(
                request,
                analysis,
                fieldUpdates,
                recordsToAdd,
                ignoredItems
        );

        processAutoTransfers(
                analysis,
                autoTransfers
        );

        LOG.debug(
                "Merge summary completed. fieldUpdates={}, autoTransfers={}, recordsToAdd={}, ignoredItems={}",
                fieldUpdates.size(),
                autoTransfers.size(),
                recordsToAdd.size(),
                ignoredItems.size()
        );

        return PatientMergeSummaryVM.builder()
                .fromPatientId(request.getFromPatientId())
                .toPatientId(request.getToPatientId())
                .fieldUpdates(fieldUpdates)
                .autoTransfers(autoTransfers)
                .recordsToAdd(recordsToAdd)
                .ignoredItems(ignoredItems)
                .build();
    }

    private void processUserDecisions(
            PatientMergeSummaryDTO request,
            PatientMergePreviewVM analysis,
            List<PatientMergeSummaryItemDTO> fieldUpdates,
            List<PatientMergeSummaryItemDTO> recordsToAdd,
            List<PatientMergeSummaryItemDTO> ignoredItems
    ) {
        if (request.getDecisions() == null || request.getDecisions().isEmpty()) {
            LOG.debug("No decisions found for summary");
            return;
        }

        for (PatientMergeDecisionDTO decision : request.getDecisions()) {

            MergeDecision finalDecision = decision.getFinalDecision();

            if (finalDecision == null) {
                continue;
            }

            PatientMergeConflictDTO sourceConflict =
                    findMatchingConflict(decision, analysis);

            switch (finalDecision) {

                case TAKE_FROM, MANUAL ->
                        fieldUpdates.add(
                                buildFieldUpdateItem(
                                        decision,
                                        sourceConflict
                                )
                        );

                case ADD_FROM_RECORD ->
                        recordsToAdd.add(
                                buildRecordToAddItem(
                                        decision,
                                        sourceConflict
                                )
                        );

                case KEEP_TO, IGNORE_FROM_RECORD ->
                        ignoredItems.add(
                                buildIgnoredItem(
                                        decision,
                                        sourceConflict
                                )
                        );

                default ->
                        LOG.trace(
                                "Skipping unsupported summary decision. finalDecision={}",
                                finalDecision
                        );
            }
        }
    }

    private void processAutoTransfers(
            PatientMergePreviewVM analysis,
            List<PatientMergeSummaryItemDTO> autoTransfers
    ) {
        if (analysis == null
                || analysis.getAutoTransfers() == null
                || analysis.getAutoTransfers().isEmpty()) {

            return;
        }

        for (PatientMergeAutoTransferDTO autoTransfer :
                analysis.getAutoTransfers()) {

            autoTransfers.add(toSummaryItem(autoTransfer));
        }
    }

    private PatientMergeSummaryItemDTO buildFieldUpdateItem(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        return toSummaryItem(
                decision,
                sourceConflict,
                decision.getToValue(),
                resolveNewValue(decision)
        );
    }

    private PatientMergeSummaryItemDTO buildRecordToAddItem(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        return toSummaryItem(
                decision,
                sourceConflict,
                "",
                decision.getSelectedValue()
        );
    }

    private PatientMergeSummaryItemDTO buildIgnoredItem(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        return toSummaryItem(
                decision,
                sourceConflict,
                decision.getToValue(),
                decision.getToValue()
        );
    }

    private String resolveNewValue(
            PatientMergeDecisionDTO decision
    ) {
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
            PatientMergeConflictDTO sourceConflict,
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
                .fieldType(resolveFieldType(decision, sourceConflict))
                .inputType(resolveInputType(decision, sourceConflict))
                .inputSource(resolveInputSource(decision, sourceConflict))
                .build();
    }

    private PatientMergeSummaryItemDTO toSummaryItem(
            PatientMergeAutoTransferDTO autoTransfer
    ) {
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
                .fieldType(autoTransfer.getFieldType())
                .inputType(autoTransfer.getInputType())
                .inputSource(autoTransfer.getInputSource())
                .build();
    }

    private String resolveFieldType(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        if (decision.getFieldType() != null) {
            return decision.getFieldType();
        }

        return sourceConflict != null
                ? sourceConflict.getFieldType()
                : null;
    }

    private String resolveInputType(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        if (decision.getInputType() != null) {
            return decision.getInputType();
        }

        return sourceConflict != null
                ? sourceConflict.getInputType()
                : null;
    }

    private String resolveInputSource(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        if (decision.getInputSource() != null) {
            return decision.getInputSource();
        }

        return sourceConflict != null
                ? sourceConflict.getInputSource()
                : null;
    }

    private PatientMergeConflictDTO findMatchingConflict(
            PatientMergeDecisionDTO decision,
            PatientMergePreviewVM analysis
    ) {
        if (analysis == null || analysis.getConflicts() == null) {
            return null;
        }

        return analysis.getConflicts()
                .stream()
                .filter(conflict ->
                        equals(conflict.getEntityName(), decision.getEntityName())
                                && equals(conflict.getTableName(), decision.getTableName())
                                && equals(conflict.getFromRecordId(), decision.getFromRecordId())
                                && equals(conflict.getToRecordId(), decision.getToRecordId())
                                && equals(conflict.getFieldName(), decision.getFieldName())
                )
                .findFirst()
                .orElse(null);
    }

    private boolean equals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private void validateRequest(
            PatientMergeSummaryDTO request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Request is required",
                    "PatientMerge",
                    "request.required"
            );
        }

        if (request.getFromPatientId() == null
                || request.getToPatientId() == null) {

            throw new BadRequestAlertException(
                    "Patient IDs are required",
                    "PatientMerge",
                    "ids.required"
            );
        }

        if (request.getFromPatientId().equals(request.getToPatientId())) {
            throw new BadRequestAlertException(
                    "Cannot merge same patient",
                    "PatientMerge",
                    "same.patient"
            );
        }
    }
}