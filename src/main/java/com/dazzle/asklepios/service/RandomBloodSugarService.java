package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.RandomBloodSugar;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.RandomBloodSugarRepository;
import com.dazzle.asklepios.service.dto.randomBloodSugar.RandomBloodSugarCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.observations.RandomBloodSugarFieldHistoryVM;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class RandomBloodSugarService {

    private static final Logger LOG = LoggerFactory.getLogger(RandomBloodSugarService.class);
    private static final String FIELD_LABEL = "Random Blood Glucose";

    private final RandomBloodSugarRepository randomBloodSugarRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final ReopenSessionAuditContext reopenSessionAuditContext;

    public RandomBloodSugar create(RandomBloodSugarCreateDTO dto) {
        LOG.info("[CREATE] RandomBloodSugar payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "randomBloodSugar",
                        "patient.notfound"
                ));
        PatientEncounter encounter = getEncounter(dto.encounterId());

        try {
            resetIsActiveForEncounterToday(encounter.getId());

            RandomBloodSugar entity = RandomBloodSugar.builder()
                    .patient(patient)
                    .encounterId(encounter.getId())
                    .randomBloodGlucose(dto.randomBloodGlucose())
                    .isActive(true)
                    .build();

            reopenSessionAuditContext.applyOpenSession(encounter.getId());
            return randomBloodSugarRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Optional<RandomBloodSugar> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_BY_ENCOUNTER] encounterId={}", encounterId);
        return randomBloodSugarRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
    }

    @Transactional(readOnly = true)
    public List<RandomBloodSugarFieldHistoryVM> findFieldHistoryByEncounterId(Long encounterId) {
        LOG.debug("[FIND_FIELD_HISTORY_BY_ENCOUNTER] encounterId={}", encounterId);

        List<RandomBloodSugar> versions =
                randomBloodSugarRepository.findByEncounterIdOrderByCreatedDateAscIdAsc(encounterId);

        if (versions == null || versions.size() < 2) {
            return List.of();
        }

        List<RandomBloodSugarFieldHistoryVM> history = new ArrayList<>();
        for (int i = 1; i < versions.size(); i++) {
            RandomBloodSugar previous = versions.get(i - 1);
            RandomBloodSugar current = versions.get(i);
            String oldValue = asText(previous.getRandomBloodGlucose());
            String newValue = asText(current.getRandomBloodGlucose());
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            history.add(
                    new RandomBloodSugarFieldHistoryVM(
                            FIELD_LABEL,
                            oldValue,
                            newValue,
                            current.getLastModifiedBy() != null
                                    ? current.getLastModifiedBy()
                                    : current.getCreatedBy(),
                            current.getCreatedDate()
                    )
            );
        }
        return history;
    }

    private void resetIsActiveForEncounterToday(Long encounterId) {
        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        randomBloodSugarRepository
                .findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
                        encounterId,
                        dayStart,
                        dayEnd
                )
                .ifPresent(entity -> {
                    entity.setIsActive(false);
                    reopenSessionAuditContext.applyOpenSession(entity.getEncounterId());
                    randomBloodSugarRepository.flush();
                });
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] RandomBloodSugar constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("fk_random_blood_sugar_patient")) {
            return new BadRequestAlertException("Invalid patient id.", "randomBloodSugar", "patient.invalid");
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving random blood sugar.",
                "randomBloodSugar",
                "db.constraint"
        );
    }

    private PatientEncounter getEncounter(Long id) {
        return patientEncounterRepository.findById(id).orElseThrow(() ->
                new BadRequestAlertException(
                        "Patient Encounter not found: " + id,
                        "randomBloodSugar",
                        "encounter.notfound"
                ));
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
