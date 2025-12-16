package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.NextOfKin;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.NextOfKinRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.nextofkin.NextOfKinCreateDTO;
import com.dazzle.asklepios.service.dto.nextofkin.NextOfKinUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NextOfKinService {

    private static final Logger LOG = LoggerFactory.getLogger(NextOfKinService.class);

    private final NextOfKinRepository nextOfKinRepository;
    private final PatientRepository patientRepository;

    public NextOfKinService(NextOfKinRepository nextOfKinRepository, PatientRepository patientRepository) {
        this.nextOfKinRepository = nextOfKinRepository;
        this.patientRepository = patientRepository;
    }

    public NextOfKin create(NextOfKinCreateDTO dto) {
        LOG.debug("Request to create NextOfKin : {}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "patient",
                        "notfound"
                ));

        NextOfKin nextOfKin = NextOfKin.builder()
                .patient(patient)
                .name(dto.name())
                .relationship(dto.relationship())
                .address(dto.address())
                .email(dto.email())
                .mobileNumber(dto.mobileNumber())
                .telephone(dto.telephone())
                .internationalNumber(dto.internationalNumber())
                .landlineNumber(dto.landlineNumber())
                .build();

        return nextOfKinRepository.save(nextOfKin);
    }

    public NextOfKin update(NextOfKinUpdateDTO dto) {
        LOG.debug("Request to update NextOfKin : {}", dto);

        NextOfKin existing = nextOfKinRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "NextOfKin not found with id " + dto.id(),
                        "nextOfKin",
                        "notfound"
                ));

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "patient",
                        "notfound"
                ));

        existing.setPatient(patient);
        existing.setName(dto.name());
        existing.setRelationship(dto.relationship());
        existing.setAddress(dto.address());
        existing.setEmail(dto.email());
        existing.setMobileNumber(dto.mobileNumber());
        existing.setTelephone(dto.telephone());
        existing.setInternationalNumber(dto.internationalNumber());
        existing.setLandlineNumber(dto.landlineNumber());

        return nextOfKinRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<NextOfKin> findByPatient(Long patientId) {
        LOG.debug("Request to get NextOfKin by patientId={}", patientId);
        return nextOfKinRepository.findByPatientId(patientId);
    }
    @Transactional(readOnly = true)
    public Optional<NextOfKin> findOne(Long id) {
        LOG.debug("Request to get NextOfKin id={}", id);
        return nextOfKinRepository.findById(id);
    }
    public void delete(Long id) {
        LOG.debug("Request to delete NextOfKin id={}", id);
        nextOfKinRepository.deleteById(id);
    }
}
