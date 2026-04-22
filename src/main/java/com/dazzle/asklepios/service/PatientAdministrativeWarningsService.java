package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import com.dazzle.asklepios.repository.PatientAdministrativeWarningsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientAdministrativeWarnings.PatientAdministrativeWarningsCreateDTO;
import com.dazzle.asklepios.service.dto.patientAdministrativeWarnings.PatientAdministrativeWarningsResolveDTO;
import com.dazzle.asklepios.service.dto.patientAdministrativeWarnings.PatientAdministrativeWarningsUndoResolveDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class PatientAdministrativeWarningsService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientAdministrativeWarningsService.class);
    private static final String ENTITY_NAME = "PatientAdministrativeWarnings";

    private final PatientAdministrativeWarningsRepository patientAdministrativeWarningsRepository;
    private final PatientRepository patientRepo;

    public PatientAdministrativeWarningsService(
            PatientAdministrativeWarningsRepository patientAdministrativeWarningsRepository,
            PatientRepository patientRepo
    ) {
        this.patientAdministrativeWarningsRepository = patientAdministrativeWarningsRepository;
        this.patientRepo = patientRepo;
    }

    /**
     * Create a new administrative warning for a patient.
     */
    public PatientAdministrativeWarnings create(PatientAdministrativeWarningsCreateDTO vm) {
        LOG.debug("create PatientAdministrativeWarnings payload={}", vm);

        Patient patient = resolvePatient(vm.patientId());

        PatientAdministrativeWarnings entity = PatientAdministrativeWarnings.builder()
                .patient(patient)
                .warningType(vm.warningType())
                .description(vm.description())
                .resolved(false)
                .resolvedBy(null)
                .resolvedDate(null)
                .undoResolvedBy(null)
                .undoResolvedDate(null)
                .build();

        PatientAdministrativeWarnings saved = patientAdministrativeWarningsRepository.save(entity);
        LOG.debug("create: saved id={}", saved.getId());
        return patientAdministrativeWarningsRepository.findById(saved.getId()).orElseThrow();
    }

    /**
     * Resolve a warning (set resolved = true, set resolvedBy / resolvedDate).
     */
    public PatientAdministrativeWarnings resolve(PatientAdministrativeWarningsResolveDTO vm) {
        LOG.debug("resolve PatientAdministrativeWarnings payload={}", vm);

        PatientAdministrativeWarnings entity = patientAdministrativeWarningsRepository.findById(vm.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientAdministrativeWarnings not found: " + vm.id(),
                        ENTITY_NAME,
                        "notfound"
                ));

        entity.setResolved(true);
        entity.setResolvedBy(vm.resolvedBy());
        entity.setResolvedDate(vm.resolvedDate() != null ? vm.resolvedDate() : Instant.now());


        PatientAdministrativeWarnings saved = patientAdministrativeWarningsRepository.save(entity);
        LOG.debug("resolve: saved id={} resolved={}", saved.getId(), saved.getResolved());
        return patientAdministrativeWarningsRepository.findById(saved.getId()).orElseThrow();
    }

    /**
     * Undo resolve (set resolved = false, set undo_resolved_by / undoResolvedDate).
     */
    public PatientAdministrativeWarnings undoResolve(PatientAdministrativeWarningsUndoResolveDTO vm) {
        LOG.debug("undoResolve PatientAdministrativeWarnings payload={}", vm);

        PatientAdministrativeWarnings entity = patientAdministrativeWarningsRepository.findById(vm.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientAdministrativeWarnings not found: " + vm.id(),
                        ENTITY_NAME,
                        "notfound"
                ));

        entity.setResolved(false);
        entity.setUndoResolvedBy(vm.undoResolvedBy());
        entity.setUndoResolvedDate(vm.undoResolvedDate() != null ? vm.undoResolvedDate() : Instant.now());

        PatientAdministrativeWarnings saved = patientAdministrativeWarningsRepository.save(entity);
        LOG.debug("undoResolve: saved id={} resolved={}", saved.getId(), saved.getResolved());
        return patientAdministrativeWarningsRepository.findById(saved.getId()).orElseThrow();
    }

    /**
     * Get paginated list of warnings for a patient.
     */
    @Transactional(readOnly = true)
    public List<PatientAdministrativeWarnings> getByPatientId(Long patientId) {
        LOG.debug("getByPatientId PatientAdministrativeWarnings patientId={}", patientId);
        return patientAdministrativeWarningsRepository.findByPatientId(patientId);
    }


    /**
     * Get paginated list of warnings for a patient filtered by warningType OR description (contains, ignore case).
     */
    @Transactional(readOnly = true)
    public List<PatientAdministrativeWarnings> searchByPatientId(Long patientId, String search) {
        LOG.debug("searchByPatientId PatientAdministrativeWarnings patientId={} search='{}'", patientId, search);

        if (search == null || search.trim().isEmpty()) {
            return patientAdministrativeWarningsRepository.findByPatientId(patientId);
        }

        String searchString = search.trim();
        return patientAdministrativeWarningsRepository.findByPatientIdAndWarningTypeContainsIgnoreCaseOrPatientIdAndDescriptionContainsIgnoreCase(
                patientId, searchString,
                patientId, searchString
        );
    }

    /**
     * Get all warnings filtered by warningType (optional).
     * If types is null/empty (or all blank), returns all warnings.
     */
    @Transactional(readOnly = true)
    public List<PatientAdministrativeWarnings> getByTypes(List<String> types) {
        LOG.debug("getByTypes PatientAdministrativeWarnings types={}", types);

        if (types == null || types.isEmpty()) {
            return patientAdministrativeWarningsRepository.findByResolvedFalse();
        }

        List<String> cleanedTypes = types.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        if (cleanedTypes.isEmpty()) {
            return patientAdministrativeWarningsRepository.findByResolvedFalse();
        }

        return patientAdministrativeWarningsRepository.findByWarningTypeInAndResolvedFalse(cleanedTypes);
    }

    /**
     * Hard delete a warning.
     */
    public void hardDelete(Long id) {
        LOG.debug("hardDelete PatientAdministrativeWarnings id={}", id);

        PatientAdministrativeWarnings entity = patientAdministrativeWarningsRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientAdministrativeWarnings not found: " + id,
                        ENTITY_NAME,
                        "notfound"
                ));

        patientAdministrativeWarningsRepository.delete(entity);
        LOG.debug("hardDelete: deleted id={}", id);
    }

    // Helpers

    private Patient resolvePatient(Long patientId) {
        LOG.debug("resolvePatient id={}", patientId);
        return patientRepo.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found: " + patientId,
                        "Patient",
                        "notfound"
                ));
    }
}
