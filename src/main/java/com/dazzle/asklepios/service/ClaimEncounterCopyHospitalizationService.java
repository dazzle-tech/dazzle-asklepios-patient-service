package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopyHospitalization;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.ClaimEncounterCopyHospitalizationRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyHospitalizationCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyHospitalizationCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyHospitalizationUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimEncounterCopyHospitalizationService {

    private final ClaimEncounterCopyHospitalizationRepository
            claimEncounterCopyHospitalizationRepository;

    @Transactional(readOnly = true)
    public List<ClaimEncounterCopyHospitalization> findByCopyId(
            Long claimEncounterCopyId,
            boolean showCancelled
    ) {
        if (showCancelled) {
            return claimEncounterCopyHospitalizationRepository
                    .findAllByClaimEncounterCopyId(claimEncounterCopyId);
        }

        return claimEncounterCopyHospitalizationRepository
                .findByClaimEncounterCopyIdAndStatusNot(
                        claimEncounterCopyId,
                        PatientHistoryStatus.CANCELLED
                );
    }

    public ClaimEncounterCopyHospitalization create(
            Long claimEncounterCopyId,
            ClaimEncounterCopyHospitalizationCreateDTO dto
    ) {
        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.facility(),
                dto.reason(),
                dto.admissionType(),
                dto.dateOfAdmission()
        );

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        ClaimEncounterCopyHospitalization entity =
                ClaimEncounterCopyHospitalization.builder()
                        .claimEncounterCopyId(claimEncounterCopyId)
                        .facility(isFree ? null : dto.facility())
                        .reason(isFree ? null : dto.reason())
                        .admissionType(isFree ? null : dto.admissionType())
                        .dateOfAdmission(
                                isFree ? null : dto.dateOfAdmission()
                        )
                        .lengthOfStayDays(
                                isFree ? null : dto.lengthOfStayDays()
                        )
                        .outcomes(isFree ? null : dto.outcomes())
                        .medicalInterventionsPerformed(
                                isFree
                                        ? null
                                        : dto.medicalInterventionsPerformed()
                        )
                        .patientIsFree(isFree)
                        .freeText(
                                isFree
                                        ? dto.freeText().trim()
                                        : null
                        )
                        .status(PatientHistoryStatus.ACTIVE)
                        .build();

        return claimEncounterCopyHospitalizationRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyHospitalization update(
            Long id,
            ClaimEncounterCopyHospitalizationUpdateDTO dto
    ) {
        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.facility(),
                dto.reason(),
                dto.admissionType(),
                dto.dateOfAdmission()
        );

        ClaimEncounterCopyHospitalization entity =
                claimEncounterCopyHospitalizationRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Claim encounter copy hospitalization not found with id " + id,
                                        "claimEncounterCopyHospitalization",
                                        "notfound"
                                )
                        );

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled hospitalization cannot be updated.",
                    "claimEncounterCopyHospitalization",
                    "cancelled"
            );
        }

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        entity.setFacility(isFree ? null : dto.facility());
        entity.setReason(isFree ? null : dto.reason());
        entity.setAdmissionType(isFree ? null : dto.admissionType());
        entity.setDateOfAdmission(
                isFree ? null : dto.dateOfAdmission()
        );
        entity.setLengthOfStayDays(
                isFree ? null : dto.lengthOfStayDays()
        );
        entity.setOutcomes(isFree ? null : dto.outcomes());
        entity.setMedicalInterventionsPerformed(
                isFree
                        ? null
                        : dto.medicalInterventionsPerformed()
        );
        entity.setPatientIsFree(isFree);
        entity.setFreeText(
                isFree
                        ? dto.freeText().trim()
                        : null
        );

        return claimEncounterCopyHospitalizationRepository
                .saveAndFlush(entity);
    }

    public ClaimEncounterCopyHospitalization cancel(
            Long id,
            ClaimEncounterCopyHospitalizationCancelDTO dto
    ) {
        ClaimEncounterCopyHospitalization entity =
                claimEncounterCopyHospitalizationRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Claim encounter copy hospitalization not found with id " + id,
                                        "claimEncounterCopyHospitalization",
                                        "notfound"
                                )
                        );

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Hospitalization is already cancelled.",
                    "claimEncounterCopyHospitalization",
                    "alreadyCancelled"
            );
        }

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(dto.cancellationReason());

        return claimEncounterCopyHospitalizationRepository
                .saveAndFlush(entity);
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "unauthenticated",
                                "claimEncounterCopyHospitalization",
                                "No authenticated user"
                        )
                );
    }

    private void validate(
            Boolean patientIsFree,
            String freeText,
            String facility,
            String reason,
            String admissionType,
            java.util.Date dateOfAdmission
    ) {
        boolean isFree = Boolean.TRUE.equals(patientIsFree);

        if (isFree) {
            if (freeText == null || freeText.trim().isEmpty()) {
                throw new BadRequestAlertException(
                        "Free text is required.",
                        "claimEncounterCopyHospitalization",
                        "freeText.required"
                );
            }

            return;
        }

        if (facility == null || facility.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Facility is required.",
                    "claimEncounterCopyHospitalization",
                    "facility.required"
            );
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Reason is required.",
                    "claimEncounterCopyHospitalization",
                    "reason.required"
            );
        }

        if (admissionType == null || admissionType.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Admission type is required.",
                    "claimEncounterCopyHospitalization",
                    "admissionType.required"
            );
        }

        if (dateOfAdmission == null) {
            throw new BadRequestAlertException(
                    "Date of admission is required.",
                    "claimEncounterCopyHospitalization",
                    "dateOfAdmission.required"
            );
        }
    }
}