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
                request != null ? request.fromPatientId() : null,
                request != null ? request.toPatientId() : null
        );

        validateRequest(request);

        PatientMergePreviewVM analysis = analysisService.analyze(
                request.fromPatientId(),
                request.toPatientId()
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

        return new PatientMergeSummaryVM(
                request.fromPatientId(),
                request.toPatientId(),
                fieldUpdates,
                recordsToAdd,
                ignoredItems,
                autoTransfers
        );
    }

    private void processUserDecisions(
            PatientMergeSummaryDTO request,
            PatientMergePreviewVM analysis,
            List<PatientMergeSummaryItemDTO> fieldUpdates,
            List<PatientMergeSummaryItemDTO> recordsToAdd,
            List<PatientMergeSummaryItemDTO> ignoredItems
    ) {
        if (request.decisions() == null || request.decisions().isEmpty()) {
            LOG.debug("No decisions found for summary");
            return;
        }

        for (PatientMergeDecisionDTO decision : request.decisions()) {

            MergeDecision finalDecision = decision.finalDecision();

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
                || analysis.autoTransfers() == null
                || analysis.autoTransfers().isEmpty()) {

            return;
        }

        for (PatientMergeAutoTransferDTO autoTransfer :
                analysis.autoTransfers()) {

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
                decision.toValue(),
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
                decision.selectedValue()
        );
    }

    private PatientMergeSummaryItemDTO buildIgnoredItem(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        return toSummaryItem(
                decision,
                sourceConflict,
                decision.toValue(),
                decision.toValue()
        );
    }

    private String resolveNewValue(
            PatientMergeDecisionDTO decision
    ) {
        if (decision.finalDecision() == MergeDecision.TAKE_FROM) {
            return decision.fromValue();
        }

        if (decision.finalDecision() == MergeDecision.MANUAL) {
            return decision.selectedValue();
        }

        return decision.selectedValue();
    }

    private PatientMergeSummaryItemDTO toSummaryItem(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict,
            String oldValue,
            String newValue
    ) {
        return new PatientMergeSummaryItemDTO(
                decision.entityName(),
                decision.tableName(),
                decision.fromRecordId(),
                decision.toRecordId(),
                decision.matchKey(),
                decision.fieldName(),
                decision.fieldLabel(),
                oldValue,
                newValue,
                decision.finalDecision(),
                resolveFieldType(decision, sourceConflict),
                resolveInputType(decision, sourceConflict),
                resolveInputSource(decision, sourceConflict)
        );
    }

    private PatientMergeSummaryItemDTO toSummaryItem(
            PatientMergeAutoTransferDTO autoTransfer
    ) {
        return new PatientMergeSummaryItemDTO(
                autoTransfer.entityName(),
                autoTransfer.tableName(),
                autoTransfer.fromRecordId(),
                autoTransfer.toRecordId(),
                autoTransfer.matchKey(),
                autoTransfer.fieldName(),
                autoTransfer.fieldLabel(),
                autoTransfer.toValue(),
                autoTransfer.selectedValue(),
                autoTransfer.suggestedDecision(),
                autoTransfer.fieldType(),
                autoTransfer.inputType(),
                autoTransfer.inputSource()
        );
    }

    private String resolveFieldType(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        if (decision.fieldType() != null) {
            return decision.fieldType();
        }

        return sourceConflict != null
                ? sourceConflict.fieldType()
                : null;
    }

    private String resolveInputType(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        if (decision.inputType() != null) {
            return decision.inputType();
        }

        return sourceConflict != null
                ? sourceConflict.inputType()
                : null;
    }

    private String resolveInputSource(
            PatientMergeDecisionDTO decision,
            PatientMergeConflictDTO sourceConflict
    ) {
        if (decision.inputSource() != null) {
            return decision.inputSource();
        }

        return sourceConflict != null
                ? sourceConflict.inputSource()
                : null;
    }

    private PatientMergeConflictDTO findMatchingConflict(
            PatientMergeDecisionDTO decision,
            PatientMergePreviewVM analysis
    ) {
        if (analysis == null || analysis.conflicts() == null) {
            return null;
        }

        return analysis.conflicts()
                .stream()
                .filter(conflict ->
                        equals(conflict.entityName(), decision.entityName())
                                && equals(conflict.tableName(), decision.tableName())
                                && equals(conflict.fromRecordId(), decision.fromRecordId())
                                && equals(conflict.toRecordId(), decision.toRecordId())
                                && equals(conflict.fieldName(), decision.fieldName())
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

        if (request.fromPatientId() == null
                || request.toPatientId() == null) {

            throw new BadRequestAlertException(
                    "Patient IDs are required",
                    "PatientMerge",
                    "ids.required"
            );
        }

        if (request.fromPatientId().equals(request.toPatientId())) {
            throw new BadRequestAlertException(
                    "Cannot merge same patient",
                    "PatientMerge",
                    "same.patient"
            );
        }
    }
}