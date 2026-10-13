package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopyCurrentMedication;
import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.UOM;
import com.dazzle.asklepios.repository.ClaimEncounterCopyCurrentMedicationRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyCurrentMedicationCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyCurrentMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyCurrentMedicationUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimEncounterCopyCurrentMedicationService {

    private final ClaimEncounterCopyCurrentMedicationRepository
            claimEncounterCopyCurrentMedicationRepository;

    @Transactional(readOnly = true)
    public List<ClaimEncounterCopyCurrentMedication> findByCopyId(
            Long claimEncounterCopyId,
            boolean showCancelled
    ) {
        if (showCancelled) {
            return claimEncounterCopyCurrentMedicationRepository
                    .findAllByClaimEncounterCopyId(claimEncounterCopyId);
        }

        return claimEncounterCopyCurrentMedicationRepository
                .findByClaimEncounterCopyIdAndStatusNot(
                        claimEncounterCopyId,
                        PatientHistoryStatus.CANCELLED
                );
    }

    public ClaimEncounterCopyCurrentMedication create(
            Long claimEncounterCopyId,
            ClaimEncounterCopyCurrentMedicationCreateDTO dto
    ) {
        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.activeIngredientId(),
                dto.startDate()
        );

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        ClaimEncounterCopyCurrentMedication entity =
                ClaimEncounterCopyCurrentMedication.builder()
                        .claimEncounterCopyId(claimEncounterCopyId)
                        .activeIngredientId(
                                isFree ? null : dto.activeIngredientId()
                        )
                        .dosage(isFree ? null : dto.dosage())
                        .unit(
                                isFree
                                        ? null
                                        : parseUnit(dto.unit())
                        )
                        .frequency(
                                isFree
                                        ? null
                                        : parseFrequency(dto.frequency())
                        )
                        .startDate(isFree ? null : dto.startDate())
                        .patientIsFree(isFree)
                        .freeText(
                                isFree
                                        ? dto.freeText().trim()
                                        : null
                        )
                        .status(PatientHistoryStatus.ACTIVE)
                        .build();

        return claimEncounterCopyCurrentMedicationRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyCurrentMedication update(
            Long id,
            ClaimEncounterCopyCurrentMedicationUpdateDTO dto
    ) {
        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.activeIngredientId(),
                dto.startDate()
        );

        ClaimEncounterCopyCurrentMedication entity =
                claimEncounterCopyCurrentMedicationRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Claim encounter copy current medication not found with id " + id,
                                        "claimEncounterCopyCurrentMedication",
                                        "notfound"
                                )
                        );

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled current medication cannot be updated.",
                    "claimEncounterCopyCurrentMedication",
                    "cancelled"
            );
        }

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        entity.setActiveIngredientId(
                isFree ? null : dto.activeIngredientId()
        );
        entity.setDosage(isFree ? null : dto.dosage());
        entity.setUnit(
                isFree
                        ? null
                        : parseUnit(dto.unit())
        );
        entity.setFrequency(
                isFree
                        ? null
                        : parseFrequency(dto.frequency())
        );
        entity.setStartDate(isFree ? null : dto.startDate());
        entity.setPatientIsFree(isFree);
        entity.setFreeText(
                isFree
                        ? dto.freeText().trim()
                        : null
        );

        return claimEncounterCopyCurrentMedicationRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyCurrentMedication cancel(
            Long id,
            ClaimEncounterCopyCurrentMedicationCancelDTO dto
    ) {
        ClaimEncounterCopyCurrentMedication entity =
                claimEncounterCopyCurrentMedicationRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Claim encounter copy current medication not found with id " + id,
                                        "claimEncounterCopyCurrentMedication",
                                        "notfound"
                                )
                        );

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Current medication is already cancelled.",
                    "claimEncounterCopyCurrentMedication",
                    "alreadyCancelled"
            );
        }

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(dto.cancellationReason());

        return claimEncounterCopyCurrentMedicationRepository
                .saveAndFlush(entity);
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "unauthenticated",
                                "claimEncounterCopyCurrentMedication",
                                "No authenticated user"
                        )
                );
    }

    private void validate(
            Boolean patientIsFree,
            String freeText,
            Long activeIngredientId,
            java.util.Date startDate
    ) {
        boolean isFree = Boolean.TRUE.equals(patientIsFree);

        if (isFree) {
            if (freeText == null || freeText.trim().isEmpty()) {
                throw new BadRequestAlertException(
                        "Free text is required.",
                        "claimEncounterCopyCurrentMedication",
                        "freeText.required"
                );
            }
            return;
        }

        if (activeIngredientId == null) {
            throw new BadRequestAlertException(
                    "Active ingredient is required.",
                    "claimEncounterCopyCurrentMedication",
                    "activeIngredient.required"
            );
        }

        if (startDate == null) {
            throw new BadRequestAlertException(
                    "Start date is required.",
                    "claimEncounterCopyCurrentMedication",
                    "startDate.required"
            );
        }

        if (startDate.after(new java.util.Date())) {
            throw new BadRequestAlertException(
                    "Start date cannot be in the future.",
                    "claimEncounterCopyCurrentMedication",
                    "startDate.future"
            );
        }
    }

    private UOM parseUnit(String unit) {
        if (unit == null || unit.trim().isEmpty()) {
            return null;
        }

        try {
            return UOM.valueOf(unit);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException(
                    "Invalid unit.",
                    "claimEncounterCopyCurrentMedication",
                    "unit.invalid"
            );
        }
    }

    private MedFrequency parseFrequency(String frequency) {
        if (frequency == null || frequency.trim().isEmpty()) {
            return null;
        }

        try {
            return MedFrequency.valueOf(frequency);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException(
                    "Invalid frequency.",
                    "claimEncounterCopyCurrentMedication",
                    "frequency.invalid"
            );
        }
    }
}
