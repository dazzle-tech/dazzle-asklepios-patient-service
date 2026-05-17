package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FamilyHistory;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.FamilyHistoryRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class FamilyHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(FamilyHistoryService.class);

    private final FamilyHistoryRepository familyHistoryRepository;
    private final PatientRepository patientRepository;

    private Patient refPatient(Long patientId) {
        return patientRepository.getReferenceById(patientId);
    }

    public FamilyHistory create(FamilyHistoryCreateDTO familyHistoryCreateDTO) {
        LOG.info("[CREATE] FamilyHistory payload={}", familyHistoryCreateDTO);

        FamilyHistory entity = FamilyHistory.builder()
                .patient(refPatient(familyHistoryCreateDTO.patientId()))
                .condition(familyHistoryCreateDTO.condition())
                .relation(familyHistoryCreateDTO.relation())
                .inheritedDiseases(familyHistoryCreateDTO.inheritedDiseases())
                .build();

        try {
            return familyHistoryRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating family history.",
                    "familyHistory",
                    "db.constraint"
            );
        }
    }

    public FamilyHistory update(FamilyHistoryUpdateDTO familyHistoryUpdateDTO) {
        LOG.info("[UPDATE] FamilyHistory payload={}", familyHistoryUpdateDTO);

        FamilyHistory entity = familyHistoryRepository.findById(familyHistoryUpdateDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Family history not found with id " + familyHistoryUpdateDTO.id(),
                        "familyHistory",
                        "notfound"
                ));

        entity.setPatient(refPatient(familyHistoryUpdateDTO.patientId()));
        entity.setCondition(familyHistoryUpdateDTO.condition());
        entity.setRelation(familyHistoryUpdateDTO.relation());
        entity.setInheritedDiseases(familyHistoryUpdateDTO.inheritedDiseases());

        try {
            return familyHistoryRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating family history.",
                    "familyHistory",
                    "db.constraint"
            );
        }
    }

    public FamilyHistory cancel(FamilyHistoryCancelDTO familyHistoryCancelDTO) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String currentUser =
                authentication != null && authentication.getName() != null
                        ? authentication.getName()
                        : "system";

        LOG.info(
                "[CANCEL] FamilyHistory id={} cancelledBy={} reason={}",
                familyHistoryCancelDTO.id(),
                currentUser,
                familyHistoryCancelDTO.cancellationReason()
        );

        FamilyHistory entity = familyHistoryRepository.findById(familyHistoryCancelDTO.id())
                .orElseThrow(() -> {
                    LOG.warn(
                            "[CANCEL] FamilyHistory not found id={}",
                            familyHistoryCancelDTO.id()
                    );
                    return new NotFoundAlertException(
                            "Family history not found with id " + familyHistoryCancelDTO.id(),
                            "familyHistory",
                            "notfound"
                    );
                });

        entity.setStatus(PatientHistoryStatus.CANCELLED);

        entity.setCancelledBy(currentUser);
        entity.setCancelledDate(new Date());
        entity.setCancellationReason(
                familyHistoryCancelDTO.cancellationReason()
        );

        try {
            FamilyHistory savedEntity =
                    familyHistoryRepository.saveAndFlush(entity);

            LOG.info(
                    "[CANCEL] FamilyHistory success id={} cancelledBy={}",
                    familyHistoryCancelDTO.id(),
                    currentUser
            );

            return savedEntity;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn(
                    "[CANCEL] FamilyHistory failed (constraint) id={} cancelledBy={}",
                    familyHistoryCancelDTO.id(),
                    currentUser,
                    ex
            );
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling family history.",
                    "familyHistory",
                    "db.constraint"
            );
        } catch (RuntimeException ex) {
            LOG.error(
                    "[CANCEL] FamilyHistory failed (unexpected) id={}",
                    familyHistoryCancelDTO.id(),
                    ex
            );
            throw ex;
        }
    }

    public void delete(Long id) {
        LOG.info("[DELETE] FamilyHistory id={}", id);

        FamilyHistory entity = familyHistoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Family history not found with id " + id,
                        "familyHistory",
                        "notfound"
                ));

        familyHistoryRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public Page<FamilyHistory> findByPatientId(
            Long patientId,
            Boolean showCancelled,
            Pageable pageable
    ) {
        LOG.debug(
                "[LIST] FamilyHistory patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        // إذا تم تفعيل Show Cancelled اعرض جميع السجلات
        if (Boolean.TRUE.equals(showCancelled)) {
            return familyHistoryRepository.findAllByPatientId(
                    patientId,
                    pageable
            );
        }

        return familyHistoryRepository.findAllByPatientIdAndStatus(
                patientId,
                PatientHistoryStatus.ACTIVE,
                pageable
        );
    }

    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("ux_patientid_condition_relation")
                || (lower.contains("unique") && lower.contains("condition"))) {
            throw new BadRequestAlertException(
                    "Family history entry already exists for this patient, condition and relation.",
                    "familyHistory",
                    "duplicate"
            );
        }

        if (lower.contains("fk_family_history_patient")
                || (lower.contains("foreign key") && lower.contains("patient"))) {
            throw new BadRequestAlertException(
                    "Invalid patient reference.",
                    "familyHistory",
                    "patient.invalid"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving family history.",
                "familyHistory",
                "db.constraint"
        );
    }
}