package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryCancelDTO;
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

import java.time.Instant;
import java.util.Date;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(SocialHistoryService.class);

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

    public SocialHistory create(SocialHistoryCreateDTO dto) {
        LOG.info("[CREATE] SocialHistory dto={}", dto);

        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.isCurrentSmoker(),
                dto.smokeStartDate(),
                dto.cigaretteAmount(),
                dto.isPreviousSmoker(),
                dto.smokeQuitDate(),
                dto.exposureToSecondHandSmoke(),
                dto.alcoholConsumption(),
                dto.alcoholSinceWhen(),
                dto.substanceUse()
        );

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        SocialHistory entity = SocialHistory.builder()
                .patient(getPatientOrThrow(dto.patientId()))
                .isCurrentSmoker(isFree ? null : dto.isCurrentSmoker())
                .smokeStartDate(isFree ? null : dto.smokeStartDate())
                .cigaretteAmount(isFree ? null : dto.cigaretteAmount())
                .cigaretteType(isFree ? null : dto.cigaretteType())
                .isPreviousSmoker(isFree ? null : dto.isPreviousSmoker())
                .smokeQuitDate(isFree ? null : dto.smokeQuitDate())
                .exposureToSecondHandSmoke(
                        isFree ? null : dto.exposureToSecondHandSmoke()
                )
                .alcoholConsumption(
                        isFree ? null : dto.alcoholConsumption()
                )
                .typeOfAlcohol(isFree ? null : dto.typeOfAlcohol())
                .alcoholSinceWhen(
                        isFree ? null : dto.alcoholSinceWhen()
                )
                .substanceUse(isFree ? null : dto.substanceUse())
                .route(isFree ? null : dto.route())
                .frequency(isFree ? null : dto.frequency())
                .physicalLimitation(
                        isFree ? null : dto.physicalLimitation()
                )
                .diagnosedEatingDisorders(
                        isFree ? null : dto.diagnosedEatingDisorders()
                )
                .patientIsFree(isFree)
                .freeText(isFree ? dto.freeText().trim() : null)
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

    public SocialHistory update(SocialHistoryUpdateDTO dto) {
        LOG.info("[UPDATE] SocialHistory dto={}", dto);

        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.isCurrentSmoker(),
                dto.smokeStartDate(),
                dto.cigaretteAmount(),
                dto.isPreviousSmoker(),
                dto.smokeQuitDate(),
                dto.exposureToSecondHandSmoke(),
                dto.alcoholConsumption(),
                dto.alcoholSinceWhen(),
                dto.substanceUse()
        );

        SocialHistory socialHistory =
                socialHistoryRepository.findById(dto.id())
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Social history not found with id " + dto.id(),
                                "socialHistory",
                                "notfound"
                        ));

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        socialHistory.setPatient(
                getPatientOrThrow(dto.patientId())
        );

        socialHistory.setIsCurrentSmoker(
                isFree ? null : dto.isCurrentSmoker()
        );

        socialHistory.setSmokeStartDate(
                isFree ? null : dto.smokeStartDate()
        );

        socialHistory.setCigaretteAmount(
                isFree ? null : dto.cigaretteAmount()
        );

        socialHistory.setCigaretteType(
                isFree ? null : dto.cigaretteType()
        );

        socialHistory.setIsPreviousSmoker(
                isFree ? null : dto.isPreviousSmoker()
        );

        socialHistory.setSmokeQuitDate(
                isFree ? null : dto.smokeQuitDate()
        );

        socialHistory.setExposureToSecondHandSmoke(
                isFree ? null : dto.exposureToSecondHandSmoke()
        );

        socialHistory.setAlcoholConsumption(
                isFree ? null : dto.alcoholConsumption()
        );

        socialHistory.setTypeOfAlcohol(
                isFree ? null : dto.typeOfAlcohol()
        );

        socialHistory.setAlcoholSinceWhen(
                isFree ? null : dto.alcoholSinceWhen()
        );

        socialHistory.setSubstanceUse(
                isFree ? null : dto.substanceUse()
        );

        socialHistory.setRoute(
                isFree ? null : dto.route()
        );

        socialHistory.setFrequency(
                isFree ? null : dto.frequency()
        );

        socialHistory.setPhysicalLimitation(
                isFree ? null : dto.physicalLimitation()
        );

        socialHistory.setDiagnosedEatingDisorders(
                isFree ? null : dto.diagnosedEatingDisorders()
        );

        socialHistory.setPatientIsFree(isFree);
        socialHistory.setFreeText(
                isFree ? dto.freeText().trim() : null
        );

        try {
            SocialHistory updated =
                    socialHistoryRepository.saveAndFlush(socialHistory);

            LOG.info(
                    "[UPDATE] SocialHistory updated id={}",
                    updated.getId()
            );

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

        SocialHistory socialHistory =
                socialHistoryRepository.findById(cancelDTO.id())
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
        socialHistory.setCancellationReason(
                cancelDTO.cancellationReason()
        );

        SocialHistory cancelled =
                socialHistoryRepository.saveAndFlush(socialHistory);

        LOG.info(
                "[CANCEL] SocialHistory cancelled id={}",
                cancelled.getId()
        );

        return cancelled;
    }

    public void delete(Long id) {
        LOG.info("[DELETE] SocialHistory id={}", id);

        SocialHistory socialHistory =
                socialHistoryRepository.findById(id)
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

    private void validate(
            Boolean patientIsFree,
            String freeText,
            Boolean isCurrentSmoker,
            Date smokeStartDate,
            Integer cigaretteAmount,
            Boolean isPreviousSmoker,
            Date smokeQuitDate,
            Boolean exposureToSecondHandSmoke,
            Boolean alcoholConsumption,
            Date alcoholSinceWhen,
            Boolean substanceUse
    ) {
        boolean isFree = Boolean.TRUE.equals(patientIsFree);

        if (isFree) {
            if (freeText == null || freeText.trim().isEmpty()) {
                throw new BadRequestAlertException(
                        "Free text is required.",
                        "socialHistory",
                        "freeText.required"
                );
            }

            return;
        }

        if (isCurrentSmoker == null) {
            throw new BadRequestAlertException(
                    "Current smoker is required.",
                    "socialHistory",
                    "currentSmoker.required"
            );
        }

        if (isPreviousSmoker == null) {
            throw new BadRequestAlertException(
                    "Previous smoker is required.",
                    "socialHistory",
                    "previousSmoker.required"
            );
        }

        if (exposureToSecondHandSmoke == null) {
            throw new BadRequestAlertException(
                    "Exposure to second hand smoke is required.",
                    "socialHistory",
                    "secondHandSmoke.required"
            );
        }

        if (alcoholConsumption == null) {
            throw new BadRequestAlertException(
                    "Alcohol consumption is required.",
                    "socialHistory",
                    "alcoholConsumption.required"
            );
        }

        if (substanceUse == null) {
            throw new BadRequestAlertException(
                    "Substance use is required.",
                    "socialHistory",
                    "substanceUse.required"
            );
        }

        if (
                Boolean.TRUE.equals(isCurrentSmoker) &&
                        Boolean.TRUE.equals(isPreviousSmoker)
        ) {
            throw new BadRequestAlertException(
                    "Patient cannot be current smoker and previous smoker at the same time.",
                    "socialHistory",
                    "smoker.conflict"
            );
        }

        if (Boolean.TRUE.equals(isCurrentSmoker)) {
            if (smokeStartDate == null || cigaretteAmount == null) {
                throw new BadRequestAlertException(
                        "Start date and cigarette amount are required for current smoker.",
                        "socialHistory",
                        "current.smoker.required"
                );
            }
        }

        if (
                Boolean.TRUE.equals(isPreviousSmoker) &&
                        smokeQuitDate == null
        ) {
            throw new BadRequestAlertException(
                    "Quit date is required for previous smoker.",
                    "socialHistory",
                    "previous.smoker.required"
            );
        }

        if (
                Boolean.TRUE.equals(alcoholConsumption) &&
                        alcoholSinceWhen == null
        ) {
            throw new BadRequestAlertException(
                    "Alcohol since-when date is required when alcohol consumption is enabled.",
                    "socialHistory",
                    "alcohol.since.required"
            );
        }

        Date now = new Date();

        if (smokeStartDate != null && smokeStartDate.after(now)) {
            throw new BadRequestAlertException(
                    "Smoke start date must be in the past or present.",
                    "socialHistory",
                    "smokeStartDate.future"
            );
        }

        if (smokeQuitDate != null && smokeQuitDate.after(now)) {
            throw new BadRequestAlertException(
                    "Smoke quit date must be in the past or present.",
                    "socialHistory",
                    "smokeQuitDate.future"
            );
        }

        if (
                alcoholSinceWhen != null &&
                        alcoholSinceWhen.after(now)
        ) {
            throw new BadRequestAlertException(
                    "Alcohol since-when date must be in the past or present.",
                    "socialHistory",
                    "alcoholSinceWhen.future"
            );
        }
    }

    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);

        String message =
                root != null
                        ? root.getMessage()
                        : exception.getMessage();

        String lower =
                message != null
                        ? message.toLowerCase()
                        : "";

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        if (lower.contains("ux_social_history_patient")) {
            throw new BadRequestAlertException(
                    "Social history already exists for this patient.",
                    "socialHistory",
                    "duplicate"
            );
        }

        if (
                lower.contains(
                        "ck_social_history_current_smoker_required"
                )
        ) {
            throw new BadRequestAlertException(
                    "Start date and cigarette amount are required for current smoker.",
                    "socialHistory",
                    "current.smoker.required"
            );
        }

        if (
                lower.contains(
                        "ck_social_history_previous_smoker_required"
                )
        ) {
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

        if (
                lower.contains(
                        "ck_social_history_alcohol_since_when_required"
                )
        ) {
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