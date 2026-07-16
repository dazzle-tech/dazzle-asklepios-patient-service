package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeExecuteDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeSummaryDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeTableConfigSaveDTO;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeValidationRuleDTO;
import com.dazzle.asklepios.service.patientMerge.PatientMergeAnalysisService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeConfigService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeExecuteService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeSummaryService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeTransactionService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeUndoService;
import com.dazzle.asklepios.service.patientMerge.PatientMergeValidationRuleService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeAvailableTableVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeExecuteVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergePreviewVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergePreviewWithValidationVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeSummaryVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTableConfigVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionChangesVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeTransactionVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeUndoVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeValidationVM;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientMergeController {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeController.class);

    private final PatientMergeAnalysisService patientMergeAnalysisService;
    private final PatientMergeSummaryService patientMergeSummaryService;
    private final PatientMergeExecuteService patientMergeExecuteService;
    private final PatientMergeUndoService patientMergeUndoService;
    private final PatientMergeTransactionService patientMergeTransactionService;
    private final PatientMergeConfigService patientMergeConfigService;
    private final PatientMergeValidationRuleService patientMergeValidationRuleService;

    public PatientMergeController(
            PatientMergeAnalysisService patientMergeAnalysisService,
            PatientMergeSummaryService patientMergeSummaryService,
            PatientMergeExecuteService patientMergeExecuteService,
            PatientMergeUndoService patientMergeUndoService,
            PatientMergeTransactionService patientMergeTransactionService,
            PatientMergeConfigService patientMergeConfigService,
            PatientMergeValidationRuleService patientMergeValidationRuleService
    ) {
        this.patientMergeAnalysisService = patientMergeAnalysisService;
        this.patientMergeSummaryService = patientMergeSummaryService;
        this.patientMergeExecuteService = patientMergeExecuteService;
        this.patientMergeUndoService = patientMergeUndoService;
        this.patientMergeTransactionService = patientMergeTransactionService;
        this.patientMergeConfigService = patientMergeConfigService;
        this.patientMergeValidationRuleService = patientMergeValidationRuleService;
    }
    @GetMapping("/patient-merge/preview/{fromPatientId}/{toPatientId}")
    public ResponseEntity<PatientMergePreviewWithValidationVM> previewMerge(
            @PathVariable @NotNull Long fromPatientId,
            @PathVariable @NotNull Long toPatientId
    ) {

        LOG.debug(
                "REST request to preview patient merge. fromPatientId={}, toPatientId={}",
                fromPatientId,
                toPatientId
        );


        PatientMergeValidationVM validation =
                patientMergeValidationRuleService.validate(fromPatientId, toPatientId);


        PatientMergePreviewVM preview =
                patientMergeAnalysisService.analyze(
                        fromPatientId,
                        toPatientId
                );

        return ResponseEntity.ok(
                new PatientMergePreviewWithValidationVM(
                        validation.valid(),
                        validation.issues(),
                        preview
                )
        );
    }

    @PostMapping("/patient-merge/summary")
    public ResponseEntity<PatientMergeSummaryVM> summarizeMerge(
            @RequestBody PatientMergeSummaryDTO request
    ) {
        LOG.debug(
                "REST request to summarize patient merge. fromPatientId={}, toPatientId={}",
                request != null ? request.fromPatientId() : null,
                request != null ? request.toPatientId() : null
        );

        return ResponseEntity.ok(
                patientMergeSummaryService.summarizeMerge(request)
        );
    }

    @PostMapping("/patient-merge/execute")
    public ResponseEntity<PatientMergeExecuteVM> executeMerge(
            @RequestBody PatientMergeExecuteDTO request
    ) {
        LOG.debug(
                "REST request to execute patient merge. fromPatientId={}, toPatientId={}",
                request != null ? request.fromPatientId() : null,
                request != null ? request.toPatientId() : null
        );
        PatientMergeValidationVM validation =

                patientMergeValidationRuleService.validate(request.fromPatientId(), request.toPatientId());

        if (!validation.valid()) {
            throw new BadRequestAlertException(
                    validation.issues().toString(),
                    "PatientMerge",
                    "merge.validation.failed"
            );
        }

        return ResponseEntity.ok(
                    patientMergeExecuteService.executeMerge(request)
            );

    }
    @PostMapping("/patient-merge/{mergeLogId}/undo")
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

    @GetMapping("/patient-merge/transactions")
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

    @GetMapping("/patient-merge/transactions/{mergeLogId}/changes")
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

    @PostMapping("/patient-merge/config/sync-missing-tables")
    public ResponseEntity<Integer> syncMissingTables() {

        Integer inserted =
                patientMergeConfigService.syncMissingTables();

        return ResponseEntity.ok(inserted);
    }

    @GetMapping("/patient-merge/config/available-patient-tables")
    public ResponseEntity<List<PatientMergeAvailableTableVM>>
    getAvailablePatientTables() {

        return ResponseEntity.ok(
                patientMergeConfigService.getAvailablePatientTables()
        );
    }

    @GetMapping("/patient-merge/config/tables")
    public ResponseEntity<List<PatientMergeTableConfigVM>>
    getTableConfigs() {

        return ResponseEntity.ok(
                patientMergeConfigService.getTableConfigs()
        );
    }
    @PostMapping("/patient-merge/config/tables")
    public ResponseEntity<PatientMergeTableConfigVM> saveTableConfig(
            @RequestBody PatientMergeTableConfigSaveDTO dto
    ) {
        return ResponseEntity.ok(
                patientMergeConfigService.saveTableConfig(dto)
        );
    }

    @GetMapping("/patient-merge/config/table-columns/{tableName}")
    public ResponseEntity<List<String>> getTableColumns(
            @PathVariable String tableName
    ) {

        return ResponseEntity.ok(
                patientMergeConfigService.getTableColumns(tableName)
        );
    }

    @PostMapping("/patient-merge/validation-rules")
    public ResponseEntity<PatientMergeValidationRuleDTO> saveValidationRule(
            @RequestBody @NotNull PatientMergeValidationRuleDTO dto
    ) {
        LOG.debug("REST request to save patient merge validation rule. code={}", dto.code());

        return ResponseEntity.ok(
                patientMergeValidationRuleService.save(dto)
        );
    }

}