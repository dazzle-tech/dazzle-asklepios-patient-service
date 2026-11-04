package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterAmendmentChangeLog;
import com.dazzle.asklepios.domain.EncounterPlanFieldAudit;
import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.VitalSignsLog;
import com.dazzle.asklepios.repository.ConsultationLogRepository;
import com.dazzle.asklepios.repository.EncounterAmendmentChangeLogRepository;
import com.dazzle.asklepios.repository.EncounterAssessmentLogRepository;
import com.dazzle.asklepios.repository.EncounterPlanFieldAuditRepository;
import com.dazzle.asklepios.repository.EncounterPlanRepository;
import com.dazzle.asklepios.repository.EncounterReopenSessionRepository;
import com.dazzle.asklepios.repository.PatientEncounterFieldAuditRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.ProgressNoteLogRepository;
import com.dazzle.asklepios.repository.VitalSignsLogRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryChangeVM;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryFieldChangeVM;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistorySessionVM;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EncounterAmendmentHistoryService {

    private final PatientEncounterRepository patientEncounterRepository;
    private final EncounterReopenSessionRepository encounterReopenSessionRepository;
    private final ProgressNoteLogRepository progressNoteLogRepository;
    private final VitalSignsLogRepository vitalSignsLogRepository;
    private final ConsultationLogRepository consultationLogRepository;
    private final PatientEncounterFieldAuditRepository patientEncounterFieldAuditRepository;
    private final EncounterAssessmentLogRepository encounterAssessmentLogRepository;
    private final EncounterPlanFieldAuditRepository encounterPlanFieldAuditRepository;
    private final EncounterPlanRepository encounterPlanRepository;
    private final EncounterAmendmentChangeLogRepository encounterAmendmentChangeLogRepository;
    private final AmendmentHistoryNormalizer normalizer;

    public EncounterAmendmentHistoryService(
            PatientEncounterRepository patientEncounterRepository,
            EncounterReopenSessionRepository encounterReopenSessionRepository,
            ProgressNoteLogRepository progressNoteLogRepository,
            VitalSignsLogRepository vitalSignsLogRepository,
            ConsultationLogRepository consultationLogRepository,
            PatientEncounterFieldAuditRepository patientEncounterFieldAuditRepository,
            EncounterAssessmentLogRepository encounterAssessmentLogRepository,
            EncounterPlanFieldAuditRepository encounterPlanFieldAuditRepository,
            EncounterPlanRepository encounterPlanRepository,
            EncounterAmendmentChangeLogRepository encounterAmendmentChangeLogRepository,
            AmendmentHistoryNormalizer normalizer
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.encounterReopenSessionRepository = encounterReopenSessionRepository;
        this.progressNoteLogRepository = progressNoteLogRepository;
        this.vitalSignsLogRepository = vitalSignsLogRepository;
        this.consultationLogRepository = consultationLogRepository;
        this.patientEncounterFieldAuditRepository = patientEncounterFieldAuditRepository;
        this.encounterAssessmentLogRepository = encounterAssessmentLogRepository;
        this.encounterPlanFieldAuditRepository = encounterPlanFieldAuditRepository;
        this.encounterPlanRepository = encounterPlanRepository;
        this.encounterAmendmentChangeLogRepository = encounterAmendmentChangeLogRepository;
        this.normalizer = normalizer;
    }

    public Page<AmendmentHistorySessionVM> findHistory(Long encounterId, Pageable pageable) {
        if (encounterId == null || !patientEncounterRepository.existsById(encounterId)) {
            throw new NotFoundAlertException(
                    "PatientEncounter not found with id " + encounterId,
                    "patientEncounter",
                    "id.notfound"
            );
        }

        Pageable safePageable = pageable == null ? Pageable.unpaged() : pageable;
        Page<EncounterReopenSession> sessionPage =
                encounterReopenSessionRepository.findByEncounter_IdOrderBySessionNumberDesc(encounterId, safePageable);
        List<EncounterReopenSession> sessions = sessionPage.getContent();
        if (sessions.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, sessionPage.getTotalElements());
        }

        Set<Long> sessionIds = sessions.stream()
                .map(EncounterReopenSession::getId)
                .collect(Collectors.toSet());
        Map<Long, List<AmendmentHistoryChangeVM>> unified = loadUnified(encounterId, sessionIds);
        Map<Long, List<AmendmentHistoryChangeVM>> legacy = loadChanges(encounterId, sessionIds);

        List<AmendmentHistorySessionVM> content = sessions.stream()
                .map(session -> {
                    List<AmendmentHistoryChangeVM> changes = mergeWithoutDuplicates(
                            legacy.getOrDefault(session.getId(), List.of()),
                            unified.getOrDefault(session.getId(), List.of())
                    );
                    return AmendmentHistorySessionVM.of(session, chronological(changes));
                })
                .toList();

        return new PageImpl<>(content, safePageable, sessionPage.getTotalElements());
    }

    private Map<Long, List<AmendmentHistoryChangeVM>> loadUnified(Long encounterId, Set<Long> sessionIds) {
        List<EncounterAmendmentChangeLog> logs = new ArrayList<>();
        for (EncounterAmendmentChangeLog log :
                encounterAmendmentChangeLogRepository.findByReopenSessionIdInOrderByChangedAtAscIdAsc(sessionIds)) {
            if (log.getReopenSessionId() == null || !sessionIds.contains(log.getReopenSessionId())) {
                continue;
            }
            if (log.getEncounterId() == null || !encounterId.equals(log.getEncounterId())) {
                continue;
            }
            logs.add(log);
        }
        return normalizer.unifiedHistory(logs, encounterId);
    }

    private Map<Long, List<AmendmentHistoryChangeVM>> loadChanges(Long encounterId, Set<Long> sessionIds) {
        Map<Long, List<AmendmentHistoryChangeVM>> changes = new LinkedHashMap<>();
        progressNoteLogRepository.findByReopenSessionIdInOrderByCreatedDateAscIdAsc(sessionIds)
                .forEach(log -> add(
                        changes,
                        sessionIds,
                        log.getReopenSessionId(),
                        normalizer.progressNote(log, encounterId)
                ));
        vitalSignsBySession(sessionIds).forEach((sessionId, logs) ->
                normalizer.vitalSigns(logs, encounterId)
                        .forEach(change -> add(changes, sessionIds, sessionId, change))
        );
        consultationLogRepository.findByReopenSessionIdInOrderByCreatedDateAscIdAsc(sessionIds)
                .forEach(log -> add(
                        changes,
                        sessionIds,
                        log.getReopenSessionId(),
                        normalizer.consultation(log, encounterId)
                ));
        patientEncounterFieldAuditRepository.findByReopenSessionIdInOrderByLogDateAscIdAsc(sessionIds)
                .forEach(log -> add(
                        changes,
                        sessionIds,
                        log.getReopenSessionId(),
                        normalizer.narrative(log, encounterId)
                ));
        encounterAssessmentLogRepository.findByReopenSessionIdInOrderByLogDateAscIdAsc(sessionIds)
                .forEach(log -> add(
                        changes,
                        sessionIds,
                        log.getReopenSessionId(),
                        normalizer.assessment(log, encounterId)
                ));
        for (EncounterPlanFieldAudit log :
                encounterPlanFieldAuditRepository.findByReopenSessionIdInOrderByLogDateAscIdAsc(sessionIds)) {
            boolean belongs = log.getEncounterPlanId() != null
                    && encounterPlanRepository.existsByIdAndEncounterId(log.getEncounterPlanId(), encounterId);
            add(changes, sessionIds, log.getReopenSessionId(), normalizer.plan(log, belongs));
        }
        return changes;
    }

    private Map<Long, List<VitalSignsLog>> vitalSignsBySession(Set<Long> sessionIds) {
        Map<Long, List<VitalSignsLog>> logsBySession = new LinkedHashMap<>();
        for (VitalSignsLog log : vitalSignsLogRepository.findByReopenSessionIdInOrderByCreatedDateAscIdAsc(sessionIds)) {
            if (log.getReopenSessionId() == null || !sessionIds.contains(log.getReopenSessionId())) {
                continue;
            }
            logsBySession.computeIfAbsent(log.getReopenSessionId(), ignored -> new ArrayList<>()).add(log);
        }
        return logsBySession;
    }

    private static List<AmendmentHistoryChangeVM> mergeWithoutDuplicates(
            List<AmendmentHistoryChangeVM> legacy,
            List<AmendmentHistoryChangeVM> unified
    ) {
        Map<String, AmendmentHistoryChangeVM> byIdentity = new LinkedHashMap<>();
        Set<String> unifiedKeys = new LinkedHashSet<>();
        for (AmendmentHistoryChangeVM change : unified) {
            if (change == null) {
                continue;
            }
            String key = identityKey(change);
            unifiedKeys.add(key);
            byIdentity.putIfAbsent(key, change);
        }
        for (AmendmentHistoryChangeVM change : legacy) {
            if (change == null) {
                continue;
            }
            String key = identityKey(change);
            if (unifiedKeys.contains(key)) {
                continue;
            }
            byIdentity.putIfAbsent(key, change);
        }
        return new ArrayList<>(byIdentity.values());
    }

    private static String identityKey(AmendmentHistoryChangeVM change) {
        return String.join(
                "|",
                Objects.toString(change.medicalSheet(), ""),
                change.action() == null ? "" : change.action().name(),
                change.recordId() == null ? "" : change.recordId().toString(),
                change.changedAt() == null ? "" : change.changedAt().toString(),
                Objects.toString(change.changedBy(), ""),
                fieldFingerprint(change.changes())
        );
    }

    private static String fieldFingerprint(List<AmendmentHistoryFieldChangeVM> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        return fields.stream()
                .map(field -> Objects.toString(field.field(), "")
                        + "="
                        + Objects.toString(field.oldValue(), "")
                        + "->"
                        + Objects.toString(field.newValue(), ""))
                .collect(Collectors.joining(";"));
    }

    private static void add(
            Map<Long, List<AmendmentHistoryChangeVM>> changes,
            Set<Long> sessionIds,
            Long sessionId,
            AmendmentHistoryChangeVM change
    ) {
        if (sessionId == null || change == null || !sessionIds.contains(sessionId)) {
            return;
        }
        changes.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(change);
    }

    private static List<AmendmentHistoryChangeVM> chronological(List<AmendmentHistoryChangeVM> changes) {
        return changes.stream()
                .sorted(Comparator
                        .comparing(AmendmentHistoryChangeVM::changedAt, Comparator.nullsLast(Instant::compareTo))
                        .thenComparing(AmendmentHistoryChangeVM::module, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AmendmentHistoryChangeVM::recordId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }
}
