package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ReviewOfSystem;
import com.dazzle.asklepios.repository.ReviewOfSystemRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.reviewofsystem.ReviewOfSystemCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.reviewofsystem.ReviewOfSystemUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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

    public ReviewOfSystemService(ReviewOfSystemRepository reviewOfSystemRepository) {
        this.reviewOfSystemRepository = reviewOfSystemRepository;
    }

    public ReviewOfSystem create(ReviewOfSystemCreateDTO dto) {
        LOG.debug("Request to create ReviewOfSystem: {}", dto);


        ReviewOfSystem ros = reviewOfSystemRepository
                .findByEncounterIdAndBodySystemAndSystemDetail(dto.encounterId(), dto.bodySystem(), dto.systemDetail())
                .orElseGet(ReviewOfSystem::new);

        ros.setPatientId(dto.patientId());
        ros.setEncounterId(dto.encounterId());
        ros.setBodySystem(dto.bodySystem());
        ros.setSystemDetail(dto.systemDetail());
        ros.setNote(dto.note());

        return reviewOfSystemRepository.save(ros);
    }

    public ReviewOfSystem update(ReviewOfSystemUpdateDTO dto) {
        LOG.debug("Request to update ReviewOfSystem: {}", dto);

        ReviewOfSystem existing = reviewOfSystemRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "ReviewOfSystem not found with id " + dto.id(),
                        "review_of_system",
                        "notfound"
                ));

        existing.setPatientId(dto.patientId());
        existing.setEncounterId(dto.encounterId());
        existing.setBodySystem(dto.bodySystem());
        existing.setSystemDetail(dto.systemDetail());
        existing.setNote(dto.note());

        return reviewOfSystemRepository.save(existing);
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
        reviewOfSystemRepository.deleteByEncounterIdAndBodySystemAndSystemDetail(encounterId, bodySystem, systemDetail);
    }
    public void delete(Long id) {
        reviewOfSystemRepository.deleteById(id);
    }
}

