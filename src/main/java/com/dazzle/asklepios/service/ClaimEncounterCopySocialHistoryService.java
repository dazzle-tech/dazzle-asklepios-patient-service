package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopySocialHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopySocialHistoryRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySocialHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySocialHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class ClaimEncounterCopySocialHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClaimEncounterCopySocialHistoryService.class);

    private final ClaimEncounterCopySocialHistoryRepository repository;
    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;

    public ClaimEncounterCopySocialHistoryService(
            ClaimEncounterCopySocialHistoryRepository repository,
            ClaimEncounterCopyRepository claimEncounterCopyRepository
    ) {
        this.repository = repository;
        this.claimEncounterCopyRepository = claimEncounterCopyRepository;
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);

        if (username == null) {
            LOG.warn(
                    "[ClaimEncounterCopySocialHistoryService] AUTH - unauthenticated request"
            );

            throw new BadRequestAlertException(
                    "unauthenticated",
                    "claimEncounterCopySocialHistory",
                    "No authenticated user"
            );
        }

        return username;
    }

    public ClaimEncounterCopySocialHistory update(
            Long id,
            ClaimEncounterCopySocialHistoryUpdateDTO dto
    ) {
        ClaimEncounterCopySocialHistory existing = findById(id);

        if (existing.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled social history cannot be updated",
                    "claimEncounterCopySocialHistory",
                    "already.cancelled"
            );
        }

        existing.setIsCurrentSmoker(dto.isCurrentSmoker());
        existing.setSmokeStartDate(dto.smokeStartDate());
        existing.setCigaretteAmount(dto.cigaretteAmount());
        existing.setCigaretteType(dto.cigaretteType());
        existing.setIsPreviousSmoker(dto.isPreviousSmoker());
        existing.setSmokeQuitDate(dto.smokeQuitDate());
        existing.setExposureToSecondHandSmoke(
                dto.exposureToSecondHandSmoke()
        );
        existing.setAlcoholConsumption(dto.alcoholConsumption());
        existing.setTypeOfAlcohol(dto.typeOfAlcohol());
        existing.setAlcoholSinceWhen(dto.alcoholSinceWhen());
        existing.setSubstanceUse(dto.substanceUse());
        existing.setRoute(dto.route());
        existing.setFrequency(dto.frequency());
        existing.setPhysicalLimitation(dto.physicalLimitation());
        existing.setDiagnosedEatingDisorders(
                dto.diagnosedEatingDisorders()
        );
        existing.setPatientIsFree(dto.patientIsFree());
        existing.setFreeText(dto.freeText());

        try {
            return repository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);

            throw new BadRequestAlertException(
                    "Database constraint violated while updating social history.",
                    "claimEncounterCopySocialHistory",
                    "db.constraint"
            );
        }
    }

    public ClaimEncounterCopySocialHistory cancel(
            Long id,
            String reason
    ) {
        ClaimEncounterCopySocialHistory existing = findById(id);

        if (existing.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Social history already cancelled",
                    "claimEncounterCopySocialHistory",
                    "already.cancelled"
            );
        }

        existing.setStatus(PatientHistoryStatus.CANCELLED);
        existing.setCancelledDate(Instant.now());
        existing.setCancelledBy(currentUsername());
        existing.setCancellationReason(reason);

        LOG.debug(
                "[CANCEL] ClaimEncounterCopySocialHistory id={} cancelledBy={}",
                id,
                existing.getCancelledBy()
        );

        try {
            return repository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);

            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling social history.",
                    "claimEncounterCopySocialHistory",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<ClaimEncounterCopySocialHistory> findByClaimEncounterCopyIdNotCancelled(
            Long claimEncounterCopyId
    ) {
        return repository.findByClaimEncounterCopyIdAndStatusNot(
                claimEncounterCopyId,
                PatientHistoryStatus.CANCELLED
        );
    }

    @Transactional(readOnly = true)
    public List<ClaimEncounterCopySocialHistory> findByClaimEncounterCopyId(
            Long claimEncounterCopyId
    ) {
        return repository.findAllByClaimEncounterCopyId(claimEncounterCopyId);
    }

    @Transactional(readOnly = true)
    public ClaimEncounterCopySocialHistory findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Social history not found",
                                "claimEncounterCopySocialHistory",
                                "notfound"
                        )
                );
    }

    private void handleConstraintsOnCreateOrUpdate(
            RuntimeException exception
    ) {
        Throwable root = getRootCause(exception);

        String message =
                root != null
                        ? root.getMessage()
                        : exception.getMessage();

        LOG.error(
                "DB ROOT CAUSE: {}",
                message,
                exception
        );

        String lower =
                message != null
                        ? message.toLowerCase()
                        : "";

        if (lower.contains(
                "cancellation_reason"
        ) || (
                lower.contains("check constraint")
                        && lower.contains("cancellation_reason")
        )) {
            throw new BadRequestAlertException(
                    "cancellationReason is required when cancelling a social history.",
                    "claimEncounterCopySocialHistory",
                    "cancellationReason.required"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving social history.",
                "claimEncounterCopySocialHistory",
                "db.constraint"
        );
    }
    public ClaimEncounterCopySocialHistory create(
            Long claimEncounterCopyId,
            ClaimEncounterCopySocialHistoryCreateDTO dto
    ) {
        if (!claimEncounterCopyRepository.existsById(claimEncounterCopyId)) {
            throw new NotFoundAlertException(
                    "Claim encounter copy not found",
                    "claimEncounterCopy",
                    "notfound"
            );
        }

        if (repository.existsByClaimEncounterCopyIdAndStatus(
                claimEncounterCopyId,
                PatientHistoryStatus.ACTIVE
        )) {
            throw new BadRequestAlertException(
                    "An active social history already exists for this claim encounter copy.",
                    "claimEncounterCopySocialHistory",
                    "active.exists"
            );
        }

        ClaimEncounterCopySocialHistory socialHistory =
                new ClaimEncounterCopySocialHistory();

        socialHistory.setClaimEncounterCopyId(claimEncounterCopyId);

        socialHistory.setIsCurrentSmoker(dto.isCurrentSmoker());
        socialHistory.setSmokeStartDate(dto.smokeStartDate());
        socialHistory.setCigaretteAmount(dto.cigaretteAmount());
        socialHistory.setCigaretteType(dto.cigaretteType());

        socialHistory.setIsPreviousSmoker(dto.isPreviousSmoker());
        socialHistory.setSmokeQuitDate(dto.smokeQuitDate());

        socialHistory.setExposureToSecondHandSmoke(
                dto.exposureToSecondHandSmoke()
        );

        socialHistory.setAlcoholConsumption(
                dto.alcoholConsumption()
        );
        socialHistory.setTypeOfAlcohol(dto.typeOfAlcohol());
        socialHistory.setAlcoholSinceWhen(dto.alcoholSinceWhen());

        socialHistory.setSubstanceUse(dto.substanceUse());
        socialHistory.setRoute(dto.route());
        socialHistory.setFrequency(dto.frequency());

        socialHistory.setPhysicalLimitation(
                dto.physicalLimitation()
        );
        socialHistory.setDiagnosedEatingDisorders(
                dto.diagnosedEatingDisorders()
        );

        socialHistory.setPatientIsFree(dto.patientIsFree());
        socialHistory.setFreeText(dto.freeText());

        socialHistory.setStatus(PatientHistoryStatus.ACTIVE);

        return repository.saveAndFlush(socialHistory);
    }
}