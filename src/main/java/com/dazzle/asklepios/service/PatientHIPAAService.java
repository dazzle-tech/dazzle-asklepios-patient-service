package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientHIPAA;
import com.dazzle.asklepios.repository.PatientHIPAARepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patient.hippa.PatientHIPAAVM;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class PatientHIPAAService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientHIPAAService.class);

    private final PatientHIPAARepository repository;

    public PatientHIPAA create(PatientHIPAAVM incoming) {

        LOG.info("[CREATE] PatientHIPAA payload={}", incoming);

        if (incoming.patientId() == null) {
            throw new BadRequestAlertException("patientId is required", "hipaa", "patientId.required");
        }

        if (repository.findByPatientId(incoming.patientId()).isPresent()) {
            throw new BadRequestAlertException("HIPAA already exists for patient", "hipaa", "unique.patient");
        }

        PatientHIPAA entity = PatientHIPAA.builder()
                .patientId(incoming.patientId())
                .noticeOfPrivacyPractice(incoming.noticeOfPrivacyPractice())
                .privacyAuthorization(incoming.privacyAuthorization())
                .noticeOfPrivacyPracticeDate(incoming.noticeOfPrivacyPracticeDate())
                .privacyAuthorizationDate(incoming.privacyAuthorizationDate())
                .build();

        return repository.save(entity);
    }


    public Optional<PatientHIPAA> update(Long patientId, PatientHIPAAVM incoming) {

        PatientHIPAA existing = repository.findByPatientId(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "HIPAA not found for patient " + patientId,
                        "hipaa",
                        "notfound"));

        existing.setNoticeOfPrivacyPractice(incoming.noticeOfPrivacyPractice());
        existing.setPrivacyAuthorization(incoming.privacyAuthorization());
        existing.setNoticeOfPrivacyPracticeDate(incoming.noticeOfPrivacyPracticeDate());
        existing.setPrivacyAuthorizationDate(incoming.privacyAuthorizationDate());

        return Optional.of(repository.save(existing));
    }

    public Optional<PatientHIPAA> findByPatientId(Long patientId) {
        return repository.findByPatientId(patientId);
    }
}
