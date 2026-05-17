package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.patientMerge.PatientMergeAnalysisService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeExecuteService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeSummaryService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeTransactionService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeUndoService;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeExecuteDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryDTO;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeExecuteVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergePreviewVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeSummaryVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionChangesVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeUndoVM;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient/patient-merge")
public class PatientMergeController {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeController.class);

    private final PatientMergeAnalysisService patientMergeAnalysisService;
    private final PatientMergeSummaryService patientMergeSummaryService;
    private final PatientMergeExecuteService patientMergeExecuteService;
    private final PatientMergeUndoService patientMergeUndoService;
    private final PatientMergeTransactionService patientMergeTransactionService;

    public PatientMergeController(
            PatientMergeAnalysisService patientMergeAnalysisService,
            PatientMergeSummaryService patientMergeSummaryService,
            PatientMergeExecuteService patientMergeExecuteService,
            PatientMergeUndoService patientMergeUndoService,
            PatientMergeTransactionService patientMergeTransactionService
    ) {
        this.patientMergeAnalysisService = patientMergeAnalysisService;
        this.patientMergeSummaryService = patientMergeSummaryService;
        this.patientMergeExecuteService = patientMergeExecuteService;
        this.patientMergeUndoService = patientMergeUndoService;
        this.patientMergeTransactionService = patientMergeTransactionService;
    }

    @GetMapping("/preview/{fromPatientId}/{toPatientId}")
    public ResponseEntity<PatientMergePreviewVM> previewMerge(
            @PathVariable @NotNull Long fromPatientId,
            @PathVariable @NotNull Long toPatientId
    ) {
        LOG.debug(
                "REST request to preview patient merge. fromPatientId={}, toPatientId={}",
                fromPatientId,
                toPatientId
        );

        return ResponseEntity.ok(
                patientMergeAnalysisService.analyze(
                        fromPatientId,
                        toPatientId
                )
        );
    }

    @PostMapping("/summary")
    public ResponseEntity<PatientMergeSummaryVM> summarizeMerge(
            @RequestBody PatientMergeSummaryDTO request
    ) {
        LOG.debug(
                "REST request to summarize patient merge. fromPatientId={}, toPatientId={}",
                request != null ? request.getFromPatientId() : null,
                request != null ? request.getToPatientId() : null
        );

        return ResponseEntity.ok(
                patientMergeSummaryService.summarizeMerge(request)
        );
    }

    @PostMapping("/execute")
    public ResponseEntity<PatientMergeExecuteVM> executeMerge(
            @RequestBody PatientMergeExecuteDTO request
    ) {
        LOG.debug(
                "REST request to execute patient merge. fromPatientId={}, toPatientId={}",
                request != null ? request.getFromPatientId() : null,
                request != null ? request.getToPatientId() : null
        );

        return ResponseEntity.ok(
                patientMergeExecuteService.executeMerge(request)
        );
    }

    @PostMapping("/{mergeLogId}/undo")
    public ResponseEntity<PatientMergeUndoVM> undoMerge(
            @PathVariable @NotNull Long mergeLogId
    ) {
        LOG.debug(
                "REST request to undo patient merge. mergeLogId={}",
                mergeLogId
        );

        return ResponseEntity.ok(
                patientMergeUndoService.undoMerge(mergeLogId)
        );
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<PatientMergeTransactionVM>> getMergeTransactions(
            @RequestParam(required = false) Long patientId
    ) {
        LOG.debug(
                "REST request to get patient merge transactions. patientId={}",
                patientId
        );

        return ResponseEntity.ok(
                patientMergeTransactionService.getTransactions(patientId)
        );
    }

    @GetMapping("/transactions/{mergeLogId}/changes")
    public ResponseEntity<PatientMergeTransactionChangesVM> getMergeTransactionChanges(
            @PathVariable @NotNull Long mergeLogId
    ) {
        LOG.debug(
                "REST request to get merge transaction changes. mergeLogId={}",
                mergeLogId
        );

        return ResponseEntity.ok(
                patientMergeTransactionService.getChanges(mergeLogId)
        );
    }
}