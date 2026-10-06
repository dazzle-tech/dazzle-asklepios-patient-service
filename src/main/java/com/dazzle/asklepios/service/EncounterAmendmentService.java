package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.TypeOfReopen;
import com.dazzle.asklepios.repository.EncounterReopenSessionRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.EncounterHasInvoiceResponse;
import com.dazzle.asklepios.service.dto.patientEncounter.EncounterAmendmentDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.EncounterAmendmentSummaryVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class EncounterAmendmentService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAmendmentService.class);
    private static final String ENTITY = "encounterAmendment";

    private final PatientEncounterRepository patientEncounterRepository;
    private final EncounterReopenSessionRepository encounterReopenSessionRepository;
    private final UserRepository userRepository;
    private final InvoiceGenerationService invoiceGenerationService;

    public EncounterAmendmentService(
            PatientEncounterRepository patientEncounterRepository,
            EncounterReopenSessionRepository encounterReopenSessionRepository,
            UserRepository userRepository,
            InvoiceGenerationService invoiceGenerationService
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.encounterReopenSessionRepository = encounterReopenSessionRepository;
        this.userRepository = userRepository;
        this.invoiceGenerationService = invoiceGenerationService;
    }

    public EncounterReopenSession startAmendment(Long encounterId, EncounterAmendmentDTO amendmentDTO) {
        LOG.info("[AMENDMENT] start encounterId={}", encounterId);

        String username = currentUsername();
        TypeOfReopen typeOfReopen = amendmentDTO == null ? null : amendmentDTO.typeOfReopen();
        if (typeOfReopen == null) {
            throw new BadRequestAlertException(
                    "A type of reopen is required to amend an encounter.",
                    ENTITY,
                    "amendment.typeRequired"
            );
        }
        String reason = amendmentDTO.reason() == null ? "" : amendmentDTO.reason().trim();
        if (reason.isBlank()) {
            throw new BadRequestAlertException(
                    "A reason is required to amend an encounter.",
                    ENTITY,
                    "amendment.reasonRequired"
            );
        }

        PatientEncounter encounter = patientEncounterRepository.findByIdForUpdate(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        User user = userRepository.findByLogin(username)
                .orElseThrow(() -> new NotFoundAlertException(
                        "User not found with login " + username,
                        "patientEncounter",
                        "user.notfound"
                ));

        TreatmentStatus currentStatus = encounter.getStatus();
        if (currentStatus != TreatmentStatus.COMPLETED && currentStatus != TreatmentStatus.DISCHARGED) {
            throw new BadRequestAlertException(
                    "Only completed or discharged encounters can be amended.",
                    ENTITY,
                    "amendment.notAllowed"
            );
        }

        EncounterHasInvoiceResponse invoiceResponse = invoiceGenerationService.hasInvoice(encounterId);
        if (invoiceResponse != null && invoiceResponse.hasInvoice()) {
            throw new BadRequestAlertException(
                    "Cannot amend encounter because an invoice has already been generated.",
                    ENTITY,
                    "amendment.invoiceExists"
            );
        }

        if (encounter.getEncounterType() == EncounterType.CLINIC && !user.isCanUnCompleteEncounter()) {
            throw new BadRequestAlertException(
                    "You do not have permission to amend outpatient encounters.",
                    ENTITY,
                    "amendment.permissionDenied"
            );
        }

        if (encounter.getEncounterType() == EncounterType.EMERGENCY && !user.isCanUnDischargeUrgentCare()) {
            throw new BadRequestAlertException(
                    "You do not have permission to amend emergency encounters.",
                    ENTITY,
                    "amendment.permissionDenied"
            );
        }

        if (encounterReopenSessionRepository.existsByEncounter_IdAndStatus(
                encounterId,
                EncounterReopenSessionStatus.OPEN
        )) {
            throw new BadRequestAlertException(
                    "This encounter already has an open amendment.",
                    ENTITY,
                    "amendment.sessionAlreadyOpen"
            );
        }

        int nextSessionNumber =
                encounterReopenSessionRepository.findMaxSessionNumberByEncounterId(encounterId) + 1;

        EncounterReopenSession session = EncounterReopenSession.builder()
                .encounter(encounter)
                .sessionNumber(nextSessionNumber)
                .typeOfReopen(typeOfReopen)
                .reason(reason)
                .originalTreatmentStatus(currentStatus)
                .reopenedBy(username)
                .reopenedAt(Instant.now())
                .status(EncounterReopenSessionStatus.OPEN)
                .completedBy(encounter.getCompletedBy())
                .completedAt(encounter.getCompletedAt())
                .dischargeAt(encounter.getDischargeAt())
                .dischargeType(encounter.getDischargeType())
                .build();

        encounter.setStatus(TreatmentStatus.ONGOING);
        encounter.setCompletedAt(null);
        encounter.setCompletedBy(null);
        encounter.setDischargeAt(null);
        encounter.setDischargeType(null);

        try {
            EncounterReopenSession savedSession = encounterReopenSessionRepository.saveAndFlush(session);
            patientEncounterRepository.saveAndFlush(encounter);
            LOG.info(
                    "[AMENDMENT] started encounterId={} sessionId={} sessionNumber={} fromStatus={}",
                    encounterId,
                    savedSession.getId(),
                    nextSessionNumber,
                    currentStatus
            );
            return savedSession;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw mapPersistenceFailure(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<EncounterReopenSession> findAmendments(Long encounterId) {
        if (encounterId == null || !patientEncounterRepository.existsById(encounterId)) {
            throw new NotFoundAlertException(
                    "PatientEncounter not found with id " + encounterId,
                    "patientEncounter",
                    "id.notfound"
            );
        }
        return encounterReopenSessionRepository.findByEncounter_IdOrderBySessionNumberAsc(encounterId);
    }

    @Transactional(readOnly = true)
    public List<EncounterAmendmentSummaryVM> findSummaries(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = encounterIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, List<EncounterReopenSession>> byEncounter = new LinkedHashMap<>();
        for (EncounterReopenSession session : encounterReopenSessionRepository.findByEncounter_IdIn(ids)) {
            Long encounterId = session.getEncounter() == null ? null : session.getEncounter().getId();
            if (encounterId == null) {
                continue;
            }
            byEncounter.computeIfAbsent(encounterId, ignored -> new ArrayList<>()).add(session);
        }

        return byEncounter.entrySet().stream()
                .map(entry -> EncounterAmendmentSummaryVM.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    public EncounterReopenSession finishAmendment(Long encounterId, Long sessionId) {
        LOG.info("[AMENDMENT] finish encounterId={} sessionId={}", encounterId, sessionId);

        String username = currentUsername();
        PatientEncounter encounter = patientEncounterRepository.findByIdForUpdate(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));
        EncounterReopenSession session = encounterReopenSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Amendment session not found with id " + sessionId,
                        ENTITY,
                        "amendment.sessionNotFound"
                ));

        Long sessionEncounterId = session.getEncounter() == null ? null : session.getEncounter().getId();
        if (!encounterId.equals(sessionEncounterId)) {
            throw new BadRequestAlertException(
                    "This amendment session does not belong to the encounter.",
                    ENTITY,
                    "amendment.sessionMismatch"
            );
        }
        if (session.getStatus() != EncounterReopenSessionStatus.OPEN) {
            throw new BadRequestAlertException(
                    "This amendment session is not open.",
                    ENTITY,
                    "amendment.sessionNotOpen"
            );
        }

        EncounterReopenSession currentOpenSession = encounterReopenSessionRepository
                .findByEncounter_IdAndStatus(encounterId, EncounterReopenSessionStatus.OPEN)
                .orElseThrow(() -> new BadRequestAlertException(
                        "This encounter does not have an open amendment.",
                        ENTITY,
                        "amendment.sessionNotOpen"
                ));
        if (!sessionId.equals(currentOpenSession.getId())) {
            throw new BadRequestAlertException(
                    "This is not the current open amendment.",
                    ENTITY,
                    "amendment.sessionNotCurrent"
            );
        }

        encounter.setStatus(session.getOriginalTreatmentStatus());
        encounter.setCompletedBy(session.getCompletedBy());
        encounter.setCompletedAt(session.getCompletedAt());
        encounter.setDischargeAt(session.getDischargeAt());
        encounter.setDischargeType(session.getDischargeType());

        try {
            patientEncounterRepository.saveAndFlush(encounter);
            session.setStatus(EncounterReopenSessionStatus.CLOSED);
            session.setClosedBy(username);
            session.setClosedAt(Instant.now());
            EncounterReopenSession closed = encounterReopenSessionRepository.saveAndFlush(session);
            LOG.info(
                    "[AMENDMENT] finished encounterId={} sessionId={} restoredStatus={}",
                    encounterId,
                    sessionId,
                    encounter.getStatus()
            );
            return closed;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw mapPersistenceFailure(ex);
        }
    }

    private RuntimeException mapPersistenceFailure(RuntimeException ex) {
        Throwable root = getRootCause(ex);
        String details = ((ex.getMessage() == null ? "" : ex.getMessage()) + " "
                + (root == null || root.getMessage() == null ? "" : root.getMessage())).toLowerCase();

        if (details.contains("ux_encounter_reopen_session_one_open")) {
            return new BadRequestAlertException(
                    "This encounter already has an open amendment.",
                    ENTITY,
                    "amendment.sessionAlreadyOpen"
            );
        }
        if (details.contains("ux_encounter_reopen_session_encounter_number")) {
            return new BadRequestAlertException(
                    "Could not assign the next amendment session number. Please retry.",
                    ENTITY,
                    "amendment.sessionConflict"
            );
        }
        return ex;
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (username == null) {
            throw new BadRequestAlertException(
                    "unauthenticated",
                    ENTITY,
                    "No authenticated user"
            );
        }
        return username;
    }
}
