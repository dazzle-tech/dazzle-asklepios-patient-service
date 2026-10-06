package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ReviewOfSystem;
import com.dazzle.asklepios.domain.enumeration.AmendmentMedicalSheet;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.ReviewOfSystemRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.reviewofsystem.ReviewOfSystemCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.reviewofsystem.ReviewOfSystemUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ReviewOfSystemService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewOfSystemService.class);

    private final ReviewOfSystemRepository reviewOfSystemRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final EncounterAmendmentAuditService amendmentAudit;

    public ReviewOfSystemService(ReviewOfSystemRepository reviewOfSystemRepository, PatientRepository patientRepository, PatientEncounterRepository patientEncounterRepository, EncounterAmendmentAuditService amendmentAudit) {
        this.reviewOfSystemRepository = reviewOfSystemRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.amendmentAudit = amendmentAudit;
    }

    public ReviewOfSystem create(ReviewOfSystemCreateDTO dto) {
        LOG.debug("Request to create ReviewOfSystem: {}", dto);


        ReviewOfSystem reviewOfSystem = reviewOfSystemRepository
                .findByEncounterIdAndBodySystemAndSystemDetail(dto.encounterId(), dto.bodySystem(), dto.systemDetail())
                .orElseGet(ReviewOfSystem::new);
        boolean creating = reviewOfSystem.getId() == null;
        Long ownedEncounterId = reviewOfSystem.getEncounterId();
        var before = creating ? null : amendmentAudit.capture(reviewOfSystem);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "reviewOfSystem",
                        "patient.notfound"
                ));
        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + dto.patientId(),
                        "reviewOfSystem",
                        "encounter.notfound"
                ));
        reviewOfSystem.setPatientId(patient.getId());
        reviewOfSystem.setEncounterId(encounter.getId());
        reviewOfSystem.setBodySystem(dto.bodySystem());
        reviewOfSystem.setSystemDetail(dto.systemDetail());
        reviewOfSystem.setNote(dto.note());

        ReviewOfSystem saved = reviewOfSystemRepository.save(reviewOfSystem);
        if (creating) {
            amendmentAudit.added(encounter.getId(), AmendmentMedicalSheet.REVIEW_OF_SYSTEMS, saved.getId(), saved);
        } else {
            amendmentAudit.changed(ownedEncounterId, AmendmentMedicalSheet.REVIEW_OF_SYSTEMS, saved.getId(), before, saved);
        }
        return saved;
    }

    public ReviewOfSystem update(ReviewOfSystemUpdateDTO dto) {
        LOG.debug("Request to update ReviewOfSystem: {}", dto);

        ReviewOfSystem existing = reviewOfSystemRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "ReviewOfSystem not found with id " + dto.id(),
                        "review_of_system",
                        "notfound"
                ));
        Long ownedEncounterId = existing.getEncounterId();
        var before = amendmentAudit.capture(existing);
        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "reviewOfSystem",
                        "patient.notfound"
                ));
        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + dto.patientId(),
                        "reviewOfSystem",
                        "encounter.notfound"
                ));
        existing.setPatientId(patient.getId());
        existing.setEncounterId(encounter.getId());
        existing.setBodySystem(dto.bodySystem());
        existing.setSystemDetail(dto.systemDetail());
        existing.setNote(dto.note());

        ReviewOfSystem saved = reviewOfSystemRepository.save(existing);
        amendmentAudit.changed(ownedEncounterId, AmendmentMedicalSheet.REVIEW_OF_SYSTEMS, saved.getId(), before, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public ReviewOfSystem findOne(Long id) {
        return reviewOfSystemRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "ReviewOfSystem not found with id " + id,
                        "review_of_system",
                        "notfound"
                ));
    }

    @Transactional(readOnly = true)
    public List<ReviewOfSystem> findByEncounter(Long encounterId) {
        return reviewOfSystemRepository.findByEncounterId(encounterId);
    }

    @Transactional(readOnly = true)
    public List<ReviewOfSystem> findByEncounterAndBodySystem(Long encounterId, String bodySystem) {
        return reviewOfSystemRepository.findByEncounterIdAndBodySystem(encounterId, bodySystem);
    }

    public void deleteByUnique(Long encounterId, String bodySystem, String systemDetail) {
        LOG.debug("Request to delete ReviewOfSystem encounterId={} bodySystem={} systemDetail={}",
                encounterId, bodySystem, systemDetail
        );
        var existing = reviewOfSystemRepository
                .findByEncounterIdAndBodySystemAndSystemDetail(encounterId, bodySystem, systemDetail);
        Long ownedEncounterId = existing.map(ReviewOfSystem::getEncounterId).orElse(null);
        var before = existing
                .map(row -> amendmentAudit.capture(row))
                .orElse(null);
        reviewOfSystemRepository.deleteByEncounterIdAndBodySystemAndSystemDetail(encounterId, bodySystem, systemDetail);
        existing.ifPresent(row -> amendmentAudit.removed(ownedEncounterId, AmendmentMedicalSheet.REVIEW_OF_SYSTEMS, row.getId(), before));
    }

    public void delete(Long id) {
        var existing = reviewOfSystemRepository.findById(id);
        Long ownedEncounterId = existing.map(ReviewOfSystem::getEncounterId).orElse(null);
        var before = existing
                .map(row -> amendmentAudit.capture(row))
                .orElse(null);
        reviewOfSystemRepository.deleteById(id);
        existing.ifPresent(row -> amendmentAudit.removed(ownedEncounterId, AmendmentMedicalSheet.REVIEW_OF_SYSTEMS, row.getId(), before));
    }
}

