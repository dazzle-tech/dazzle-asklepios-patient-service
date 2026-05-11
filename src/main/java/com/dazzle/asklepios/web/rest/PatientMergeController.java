package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PatientMergeExecuteService;
import com.dazzle.asklepios.service.PatientMergeService;
import com.dazzle.asklepios.service.PatientMergeSummaryService;
import com.dazzle.asklepios.service.PatientMergeUndoService;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeExecuteRequest;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeExecuteResponse;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergePreviewResponse;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryRequest;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryResponse;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeUndoResponse;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionChangesVM;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.dazzle.asklepios.service.PatientMergeTransactionService;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionVM;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
@RestController
@RequestMapping("/api/patient/patient-merge")
public class PatientMergeController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientMergeController.class);

    private final PatientMergeService patientMergeService;
    private final PatientMergeExecuteService patientMergeExecuteService;
    private final PatientMergeSummaryService patientMergeSummaryService;
    private final PatientMergeUndoService patientMergeUndoService;
    private final PatientMergeTransactionService patientMergeTransactionService;
    public PatientMergeController(
            PatientMergeService patientMergeService,
            PatientMergeExecuteService patientMergeExecuteService,
            PatientMergeSummaryService patientMergeSummaryService,
            PatientMergeUndoService patientMergeUndoService, PatientMergeTransactionService patientMergeTransactionService
    ) {
        this.patientMergeService = patientMergeService;
        this.patientMergeExecuteService = patientMergeExecuteService;
        this.patientMergeSummaryService = patientMergeSummaryService;
        this.patientMergeUndoService = patientMergeUndoService;
        this.patientMergeTransactionService = patientMergeTransactionService;
    }

    @GetMapping("/preview/{fromPatientId}/{toPatientId}")
    public ResponseEntity<PatientMergePreviewResponse> previewMerge(
            @PathVariable @NotNull Long fromPatientId,
            @PathVariable @NotNull Long toPatientId
    ) {
        LOG.debug("REST previewMerge from patient {} to patient {}", fromPatientId, toPatientId);
        return ResponseEntity.ok(patientMergeService.previewMerge(fromPatientId, toPatientId));
    }

    @PostMapping("/summary")
    public ResponseEntity<PatientMergeSummaryResponse> summarizeMerge(
            @RequestBody PatientMergeSummaryRequest request
    ) {
        LOG.debug("REST summarizeMerge from patient {} to patient {}", request.getFromPatientId(), request.getToPatientId());
        return ResponseEntity.ok(patientMergeSummaryService.summarizeMerge(request));
    }

    @PostMapping("/execute")
    public ResponseEntity<PatientMergeExecuteResponse> executeMerge(
            @RequestBody PatientMergeExecuteRequest request
    ) {
        LOG.debug("REST executeMerge from patient {} to patient {}", request.getFromPatientId(), request.getToPatientId());
        return ResponseEntity.ok(patientMergeExecuteService.executeMerge(request));
    }

    @PostMapping("/{mergeLogId}/undo")
    public ResponseEntity<PatientMergeUndoResponse> undoMerge(
            @PathVariable @NotNull Long mergeLogId
    ) {
        LOG.debug("REST undoMerge for merge log {}", mergeLogId);
        return ResponseEntity.ok(patientMergeUndoService.undoMerge(mergeLogId));
    }
    @GetMapping("/transactions")
    public ResponseEntity<List<PatientMergeTransactionVM>> getMergeTransactions(
            @RequestParam(required = false) Long patientId
    ) {
        LOG.debug("REST getMergeTransactions patientId {}", patientId);

        return ResponseEntity.ok(
                patientMergeTransactionService.getTransactions(patientId)
        );
    }


    @GetMapping("/transactions/{mergeLogId}/changes")
    public ResponseEntity<PatientMergeTransactionChangesVM> getMergeTransactionChanges(
            @PathVariable @NotNull Long mergeLogId
    ) {

        LOG.debug("REST getMergeTransactionChanges mergeLogId {}", mergeLogId);

        return ResponseEntity.ok(
                patientMergeTransactionService.getChanges(mergeLogId)
        );
    }
}