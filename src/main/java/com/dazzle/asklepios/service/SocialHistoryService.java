package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryCancelDTO;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;

import java.time.Instant;
import java.util.Date;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(SocialHistoryService.class);

    private final SocialHistoryRepository socialHistoryRepository;
    private final PatientRepository patientRepository;

    private Patient getPatientOrThrow(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "socialHistory",
                        "patient.notfound"
                ));
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "socialHistory",
                        "No authenticated user"
                ));
    }

    public SocialHistory create(SocialHistoryCreateDTO socialHistoryCreateDTO) {
        LOG.info("[CREATE] SocialHistory dto={}", socialHistoryCreateDTO);

        SocialHistory entity = SocialHistory.builder()
                // Use the existing helper method in this service
                .patient(getPatientOrThrow(socialHistoryCreateDTO.patientId()))
                .isCurrentSmoker(socialHistoryCreateDTO.isCurrentSmoker())
                .smokeStartDate(socialHistoryCreateDTO.smokeStartDate())
                .cigaretteAmount(socialHistoryCreateDTO.cigaretteAmount())
                .cigaretteType(socialHistoryCreateDTO.cigaretteType())
                .isPreviousSmoker(socialHistoryCreateDTO.isPreviousSmoker())
                .smokeQuitDate(socialHistoryCreateDTO.smokeQuitDate())
                .exposureToSecondHandSmoke(
                        socialHistoryCreateDTO.exposureToSecondHandSmoke()
                )
                .alcoholConsumption(socialHistoryCreateDTO.alcoholConsumption())
                .typeOfAlcohol(socialHistoryCreateDTO.typeOfAlcohol())
                .alcoholSinceWhen(socialHistoryCreateDTO.alcoholSinceWhen())
                .substanceUse(socialHistoryCreateDTO.substanceUse())
                .route(socialHistoryCreateDTO.route())
                .frequency(socialHistoryCreateDTO.frequency())
                .physicalLimitation(socialHistoryCreateDTO.physicalLimitation())
                .diagnosedEatingDisorders(
                        socialHistoryCreateDTO.diagnosedEatingDisorders()
                )

                .status(PatientHistoryStatus.ACTIVE)

                .build();

        try {
            return socialHistoryRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving social history.",
                    "socialHistory",
                    "db.constraint"
            );
        }
    }

    public SocialHistory update(SocialHistoryUpdateDTO socialHistoryUpdateDTO) {
        LOG.info("[UPDATE] SocialHistory dto={}", socialHistoryUpdateDTO);

        SocialHistory socialHistory = socialHistoryRepository.findById(socialHistoryUpdateDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Social history not found with id " + socialHistoryUpdateDTO.id(),
                        "socialHistory",
                        "notfound"
                ));

        Patient patient = getPatientOrThrow(socialHistoryUpdateDTO.patientId());

        socialHistory.setPatient(patient);
        socialHistory.setIsCurrentSmoker(Boolean.TRUE.equals(socialHistoryUpdateDTO.isCurrentSmoker()));
        socialHistory.setIsPreviousSmoker(Boolean.TRUE.equals(socialHistoryUpdateDTO.isPreviousSmoker()));
        socialHistory.setExposureToSecondHandSmoke(Boolean.TRUE.equals(socialHistoryUpdateDTO.exposureToSecondHandSmoke()));
        socialHistory.setAlcoholConsumption(Boolean.TRUE.equals(socialHistoryUpdateDTO.alcoholConsumption()));
        socialHistory.setSubstanceUse(Boolean.TRUE.equals(socialHistoryUpdateDTO.substanceUse()));

        socialHistory.setSmokeStartDate(socialHistoryUpdateDTO.smokeStartDate());
        socialHistory.setCigaretteAmount(socialHistoryUpdateDTO.cigaretteAmount());
        socialHistory.setCigaretteType(socialHistoryUpdateDTO.cigaretteType());
        socialHistory.setSmokeQuitDate(socialHistoryUpdateDTO.smokeQuitDate());
        socialHistory.setAlcoholSinceWhen(socialHistoryUpdateDTO.alcoholSinceWhen());
        socialHistory.setTypeOfAlcohol(socialHistoryUpdateDTO.typeOfAlcohol());
        socialHistory.setRoute(socialHistoryUpdateDTO.route());
        socialHistory.setFrequency(socialHistoryUpdateDTO.frequency());
        socialHistory.setPhysicalLimitation(socialHistoryUpdateDTO.physicalLimitation());
        socialHistory.setDiagnosedEatingDisorders(socialHistoryUpdateDTO.diagnosedEatingDisorders());

        try {
            SocialHistory updated = socialHistoryRepository.saveAndFlush(socialHistory);
            LOG.info("[UPDATE] SocialHistory updated id={}", updated.getId());
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating social history.",
                    "socialHistory",
                    "db.constraint"
            );
        }
    }

    public SocialHistory cancel(SocialHistoryCancelDTO cancelDTO) {
        LOG.info("[CANCEL] SocialHistory dto={}", cancelDTO);

        SocialHistory socialHistory = socialHistoryRepository.findById(cancelDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Social history not found with id " + cancelDTO.id(),
                        "socialHistory",
                        "notfound"
                ));

        if (socialHistory.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Social history is already cancelled.",
                    "socialHistory",
                    "already.cancelled"
            );
        }

        socialHistory.setStatus(PatientHistoryStatus.CANCELLED);
        socialHistory.setCancelledBy(currentUsername());
        socialHistory.setCancelledDate(Instant.now());
        socialHistory.setCancellationReason(cancelDTO.cancellationReason());
        SocialHistory cancelled = socialHistoryRepository.saveAndFlush(socialHistory);

        LOG.info("[CANCEL] SocialHistory cancelled id={}", cancelled.getId());

        return cancelled;
    }

    public void delete(Long id) {
        LOG.info("[DELETE] SocialHistory id={}", id);

        SocialHistory socialHistory = socialHistoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Social history not found with id " + id,
                        "socialHistory",
                        "notfound"
                ));

        socialHistoryRepository.delete(socialHistory);
    }

    @Transactional(readOnly = true)
    public Page<SocialHistory> findByPatientId(
            Long patientId,
            boolean showCancelled,
            Pageable pageable
    ) {
        LOG.debug(
                "[LIST] SocialHistory patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        if (showCancelled) {
            return socialHistoryRepository.findAllByPatientId(
                    patientId,
                    pageable
            );
        }

        return socialHistoryRepository.findAllByPatientIdAndStatusNot(
                patientId,
                PatientHistoryStatus.CANCELLED,
                pageable
        );
    }

    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());
        String lower = message != null ? message.toLowerCase() : "";

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        if (lower.contains("ux_social_history_patient")) {
            throw new BadRequestAlertException(
                    "Social history already exists for this patient.",
                    "socialHistory",
                    "duplicate"
            );
        }

        if (lower.contains("ck_social_history_current_smoker_required")) {
            throw new BadRequestAlertException(
                    "Start date and cigarette amount are required for current smoker.",
                    "socialHistory",
                    "current.smoker.required"
            );
        }

        if (lower.contains("ck_social_history_previous_smoker_required")) {
            throw new BadRequestAlertException(
                    "Quit date is required for previous smoker.",
                    "socialHistory",
                    "previous.smoker.required"
            );
        }

        if (lower.contains("ck_social_history_smoker_xor")) {
            throw new BadRequestAlertException(
                    "Patient cannot be current smoker and previous smoker at the same time.",
                    "socialHistory",
                    "smoker.conflict"
            );
        }

        if (lower.contains("ck_social_history_alcohol_since_when_required")) {
            throw new BadRequestAlertException(
                    "Alcohol since-when date is required when alcohol consumption is enabled.",
                    "socialHistory",
                    "alcohol.since.required"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving social history.",
                "socialHistory",
                "db.constraint"
        );
    }
}
