package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientAdministrativeWarnings;
import com.dazzle.asklepios.repository.PatientAdministrativeWarningsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings.PatientAdministrativeWarningsCreateVM;
import com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings.PatientAdministrativeWarningsResolveVM;
import com.dazzle.asklepios.web.rest.vm.patientAdministrativeWarnings.PatientAdministrativeWarningsUndoResolveVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PatientAdministrativeWarningsService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientAdministrativeWarningsService.class);
    private static final String ENTITY_NAME = "PatientAdministrativeWarnings";

    private final PatientAdministrativeWarningsRepository pawRepo;
    private final PatientRepository patientRepo;

    public PatientAdministrativeWarningsService(
            PatientAdministrativeWarningsRepository pawRepo,
            PatientRepository patientRepo
    ) {
        this.pawRepo = pawRepo;
        this.patientRepo = patientRepo;
    }

    /**
     * Create a new administrative warning for a patient.
     */
    public PatientAdministrativeWarnings create(PatientAdministrativeWarningsCreateVM vm) {
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

        PatientAdministrativeWarnings saved = pawRepo.save(entity);
        LOG.debug("create: saved id={}", saved.getId());
        return saved;
    }

    /**
     * Resolve a warning (set resolved = true, set resolvedBy / resolvedDate).
     */
    public PatientAdministrativeWarnings resolve(PatientAdministrativeWarningsResolveVM vm) {
        LOG.debug("resolve PatientAdministrativeWarnings payload={}", vm);

        PatientAdministrativeWarnings entity = pawRepo.findById(vm.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientAdministrativeWarnings not found: " + vm.id(),
                        ENTITY_NAME,
                        "notfound"
                ));

        entity.setResolved(true);
        entity.setResolvedBy(vm.resolvedBy());
        entity.setResolvedDate(vm.resolvedDate() != null ? vm.resolvedDate() : Instant.now());


        PatientAdministrativeWarnings saved = pawRepo.save(entity);
        LOG.debug("resolve: saved id={} resolved={}", saved.getId(), saved.getResolved());
        return saved;
    }

    /**
     * Undo resolve (set resolved = false, set undo_resolved_by / undoResolvedDate).
     */
    public PatientAdministrativeWarnings undoResolve(PatientAdministrativeWarningsUndoResolveVM vm) {
        LOG.debug("undoResolve PatientAdministrativeWarnings payload={}", vm);

        PatientAdministrativeWarnings entity = pawRepo.findById(vm.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientAdministrativeWarnings not found: " + vm.id(),
                        ENTITY_NAME,
                        "notfound"
                ));

        entity.setResolved(false);
        entity.setUndoResolvedBy(vm.undoResolvedBy());
        entity.setUndoResolvedDate(vm.undoResolvedDate() != null ? vm.undoResolvedDate() : Instant.now());

        PatientAdministrativeWarnings saved = pawRepo.save(entity);
        LOG.debug("undoResolve: saved id={} resolved={}", saved.getId(), saved.getResolved());
        return saved;
    }

    /**
     * Get paginated list of warnings for a patient.
     */
    @Transactional(readOnly = true)
    public List<PatientAdministrativeWarnings> getByPatientId(Long patientId) {
        LOG.debug("getByPatientId PatientAdministrativeWarnings patientId={}", patientId);
        return pawRepo.findByPatientId(patientId);
    }


    /**
     * Get paginated list of warnings for a patient filtered by warningType OR description (contains, ignore case).
     */
    @Transactional(readOnly = true)
    public List<PatientAdministrativeWarnings> getByPatientIdAndQuery(Long patientId, String search) {
        LOG.debug("getByPatientIdAndQuery PatientAdministrativeWarnings patientId={} search='{}'", patientId, search);

        if (search == null || search.trim().isEmpty()) {
            return pawRepo.findByPatientId(patientId);
        }

        String searchString = search.trim();
        return pawRepo.findByPatientIdAndWarningTypeContainsIgnoreCaseOrPatientIdAndDescriptionContainsIgnoreCase(
                patientId, searchString,
                patientId, searchString
        );
    }

    /**
     * Hard delete a warning.
     */
    public void hardDelete(Long id) {
        LOG.debug("hardDelete PatientAdministrativeWarnings id={}", id);

        PatientAdministrativeWarnings entity = pawRepo.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientAdministrativeWarnings not found: " + id,
                        ENTITY_NAME,
                        "notfound"
                ));

        pawRepo.delete(entity);
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
