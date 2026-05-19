package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientMergeItemLog;
import com.dazzle.asklepios.domain.PatientMergeLog;
import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import com.dazzle.asklepios.domain.enumeration.MergeDecision;
import com.dazzle.asklepios.domain.enumeration.PatientStatus;
import com.dazzle.asklepios.repository.PatientMergeItemLogRepository;
import com.dazzle.asklepios.repository.PatientMergeLogRepository;
import com.dazzle.asklepios.repository.PatientMergeMasterDecisionRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeUndoVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PatientMergeUndoService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientMergeUndoService.class);

    private final PatientMergeLogRepository patientMergeLogRepository;
    private final PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository;
    private final PatientMergeItemLogRepository patientMergeItemLogRepository;
    private final PatientRepository patientRepository;
    private final PatientMergeSupportService supportService;
    private final JdbcTemplate jdbcTemplate;

    public PatientMergeUndoService(
            PatientMergeLogRepository patientMergeLogRepository,
            PatientMergeMasterDecisionRepository patientMergeMasterDecisionRepository,
            PatientMergeItemLogRepository patientMergeItemLogRepository,
            PatientRepository patientRepository,
            PatientMergeSupportService supportService,
            JdbcTemplate jdbcTemplate
    ) {
        this.patientMergeLogRepository = patientMergeLogRepository;
        this.patientMergeMasterDecisionRepository = patientMergeMasterDecisionRepository;
        this.patientMergeItemLogRepository = patientMergeItemLogRepository;
        this.patientRepository = patientRepository;
        this.supportService = supportService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PatientMergeUndoVM undoMerge(Long mergeLogId) {
        LOG.debug("Undoing patient merge. mergeLogId={}", mergeLogId);

        validateMergeLogId(mergeLogId);

        PatientMergeLog mergeLog = patientMergeLogRepository
                .findById(mergeLogId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Merge log not found",
                                "PatientMergeLog",
                                mergeLogId.toString()
                        )
                );

        validateUndoAllowed(mergeLog);

        int restoredFieldsCount = restoreFieldChanges(mergeLog);
        int restoredRecordsCount = restoreMovedRecords(mergeLog);

        restoreSourcePatient(mergeLog);
        markMergeAsUndone(mergeLog);

        LOG.info(
                "Patient merge undone successfully. mergeLogId={}, restoredFields={}, restoredRecords={}",
                mergeLogId,
                restoredFieldsCount,
                restoredRecordsCount
        );

        return new PatientMergeUndoVM(
                mergeLog.getId(),
                mergeLog.getFromPatient().getId(),
                mergeLog.getToPatient().getId(),
                "UNDONE",
                restoredFieldsCount,
                restoredRecordsCount
        );
    }

    private void validateMergeLogId(Long mergeLogId) {
        if (mergeLogId == null) {
            throw new BadRequestAlertException(
                    "Merge log id is required",
                    "PatientMerge",
                    "merge.log.id.required"
            );
        }
    }

    private void validateUndoAllowed(PatientMergeLog mergeLog) {
        if (!"MERGED".equals(mergeLog.getMergeStatus())) {
            throw new BadRequestAlertException(
                    "Only MERGED records can be undone",
                    "PatientMerge",
                    "merge.not.active"
            );
        }

        boolean hasNewerMerges =
                patientMergeLogRepository.existsByToPatientIdAndMergedAtAfterAndMergeStatus(
                        mergeLog.getToPatient().getId(),
                        mergeLog.getMergedAt(),
                        "MERGED"
                );

        if (hasNewerMerges) {
            throw new BadRequestAlertException(
                    "newer.merges.exist",
                    "PatientMerge",
                    "Cannot undo merge because newer merges exist on target patient"
            );
        }
    }

    private int restoreFieldChanges(PatientMergeLog mergeLog) {
        List<PatientMergeMasterDecision> restoredDecisions =
                patientMergeMasterDecisionRepository
                        .findByMergeLogId(mergeLog.getId())
                        .stream()
                        .filter(this::shouldRestoreField)
                        .peek(this::restoreField)
                        .toList();

        LOG.debug(
                "Restored {} field changes. mergeLogId={}",
                restoredDecisions.size(),
                mergeLog.getId()
        );

        return restoredDecisions.size();
    }

    private boolean shouldRestoreField(PatientMergeMasterDecision decision) {
        if (decision.getFieldName() == null || decision.getFieldName().isBlank()) {
            return false;
        }

        if (!"PATIENT".equals(decision.getEntityName())) {
            return false;
        }

        return decision.getFinalDecision() == MergeDecision.TAKE_FROM
                || decision.getFinalDecision() == MergeDecision.MANUAL;
    }

    private void restoreField(PatientMergeMasterDecision decision) {
        supportService.validateIdentifier(decision.getTableName());
        supportService.validateIdentifier(decision.getFieldName());

        String sql = "UPDATE " + decision.getTableName()
                + " SET " + decision.getFieldName() + " = ?"
                + " WHERE id = ?";

        LOG.debug(
                "Restoring field value. tableName={}, fieldName={}, recordId={}",
                decision.getTableName(),
                decision.getFieldName(),
                decision.getToRecordId()
        );

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);

            supportService.setPreparedStatementValue(
                    ps,
                    1,
                    decision.getToValue(),
                    decision.getTableName(),
                    decision.getFieldName()
            );

            ps.setLong(2, decision.getToRecordId());

            return ps;
        });
    }

    private int restoreMovedRecords(PatientMergeLog mergeLog) {
        List<PatientMergeItemLog> restoredItems =
                patientMergeItemLogRepository
                        .findByMergeLogId(mergeLog.getId())
                        .stream()
                        .peek(this::restoreMovedRecord)
                        .toList();

        LOG.debug(
                "Restored {} moved records. mergeLogId={}",
                restoredItems.size(),
                mergeLog.getId()
        );

        return restoredItems.size();
    }

    private void restoreMovedRecord(PatientMergeItemLog item) {
        restoreMovedRecord(
                item.getTableName(),
                item.getRecordId(),
                item.getOldPatientId()
        );
    }

    private void restoreMovedRecord(
            String tableName,
            Long recordId,
            Long oldPatientId
    ) {
        supportService.validateIdentifier(tableName);

        String sql = "UPDATE " + tableName
                + " SET patient_id = ?"
                + " WHERE id = ?";

        LOG.debug(
                "Restoring moved record. tableName={}, recordId={}, oldPatientId={}",
                tableName,
                recordId,
                oldPatientId
        );

        jdbcTemplate.update(sql, oldPatientId, recordId);
    }

    private void restoreSourcePatient(PatientMergeLog mergeLog) {
        Patient fromPatient = mergeLog.getFromPatient();

        fromPatient.setPatientStatus(PatientStatus.ACTIVE);
        fromPatient.setMergedIntoPatientId(null);
        fromPatient.setMergedAt(null);
        fromPatient.setMergedBy(null);
        fromPatient.setMergeNote(null);

        patientRepository.save(fromPatient);

        LOG.debug(
                "Restored source patient status. patientId={}",
                fromPatient.getId()
        );
    }

    private void markMergeAsUndone(PatientMergeLog mergeLog) {
        mergeLog.setMergeStatus("UNDONE");
        mergeLog.setUndoneAt(Instant.now());
        mergeLog.setUndoneBy(currentUsername());

        patientMergeLogRepository.save(mergeLog);

        LOG.debug(
                "Marked merge as undone. mergeLogId={}",
                mergeLog.getId()
        );
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "unauthenticated",
                                "diagnostic_order_tests_result",
                                "No authenticated user"
                        )
                );
    }
}