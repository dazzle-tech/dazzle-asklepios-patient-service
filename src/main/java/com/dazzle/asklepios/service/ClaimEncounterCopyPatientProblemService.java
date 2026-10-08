package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopyPatientProblem;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.ClaimEncounterCopyPatientProblemRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyPatientProblemCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyPatientProblemCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyPatientProblemUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimEncounterCopyPatientProblemService {

    private final ClaimEncounterCopyPatientProblemRepository repository;
    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;

    public List<ClaimEncounterCopyPatientProblem> findByCopyId(
            Long claimEncounterCopyId,
            boolean showCancelled
    ) {
        if (showCancelled) {
            return repository.findAllByClaimEncounterCopyId(
                    claimEncounterCopyId
            );
        }

        return repository.findByClaimEncounterCopyIdAndStatusNot(
                claimEncounterCopyId,
                PatientHistoryStatus.CANCELLED
        );
    }

    public ClaimEncounterCopyPatientProblem update(
            Long id,
            ClaimEncounterCopyPatientProblemUpdateDTO dto
    ) {
        ClaimEncounterCopyPatientProblem entity =
                repository.findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy patient problem not found",
                                "claimEncounterCopyPatientProblem",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled patient problem cannot be updated.",
                    "claimEncounterCopyPatientProblem",
                    "cancelled"
            );
        }

        validateRequiredFields(dto);

        applyUpdate(entity, dto);

        return repository.saveAndFlush(entity);
    }

    public ClaimEncounterCopyPatientProblem cancel(
            Long id,
            ClaimEncounterCopyPatientProblemCancelDTO dto
    ) {
        ClaimEncounterCopyPatientProblem entity =
                repository.findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy patient problem not found",
                                "claimEncounterCopyPatientProblem",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Patient problem is already cancelled.",
                    "claimEncounterCopyPatientProblem",
                    "already.cancelled"
            );
        }

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(
                SecurityUtils.getCurrentUserLogin().orElse(null)
        );
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(
                dto.cancellationReason()
        );

        return repository.saveAndFlush(entity);
    }

    public ClaimEncounterCopyPatientProblem create(
            Long claimEncounterCopyId,
            ClaimEncounterCopyPatientProblemCreateDTO dto
    ) {
        if (!claimEncounterCopyRepository.existsById(
                claimEncounterCopyId
        )) {
            throw new NotFoundAlertException(
                    "Claim encounter copy not found",
                    "claimEncounterCopy",
                    "notfound"
            );
        }

        validateRequiredFields(dto);

        ClaimEncounterCopyPatientProblem entity =
                new ClaimEncounterCopyPatientProblem();

        entity.setClaimEncounterCopyId(claimEncounterCopyId);

        applyCreate(entity, dto);

        entity.setStatus(PatientHistoryStatus.ACTIVE);

        return repository.saveAndFlush(entity);
    }

    private void applyCreate(
            ClaimEncounterCopyPatientProblem entity,
            ClaimEncounterCopyPatientProblemCreateDTO dto
    ) {
        boolean isFree = Boolean.TRUE.equals(
                dto.patientIsFree()
        );

        if (isFree) {
            entity.setCondition(null);
            entity.setDateOfDiagnosis(null);
            entity.setConditionStatus(null);
            entity.setType(null);
            entity.setDateOfResolution(null);
            entity.setByPatient(null);
            entity.setSourceOfInformation(null);
            entity.setFreeText(
                    dto.freeText() != null
                            ? dto.freeText().trim()
                            : null
            );
        } else {
            entity.setCondition(dto.condition());
            entity.setDateOfDiagnosis(dto.dateOfDiagnosis());
            entity.setConditionStatus(dto.conditionStatus());
            entity.setType(dto.type());
            entity.setDateOfResolution(dto.dateOfResolution());
            entity.setByPatient(dto.byPatient());
            entity.setSourceOfInformation(
                    dto.sourceOfInformation()
            );
            entity.setFreeText(null);
        }

        entity.setPatientIsFree(isFree);
    }

    private void applyUpdate(
            ClaimEncounterCopyPatientProblem entity,
            ClaimEncounterCopyPatientProblemUpdateDTO dto
    ) {
        boolean isFree = Boolean.TRUE.equals(
                dto.patientIsFree()
        );

        if (isFree) {
            entity.setCondition(null);
            entity.setDateOfDiagnosis(null);
            entity.setConditionStatus(null);
            entity.setType(null);
            entity.setDateOfResolution(null);
            entity.setByPatient(null);
            entity.setSourceOfInformation(null);
            entity.setFreeText(
                    dto.freeText() != null
                            ? dto.freeText().trim()
                            : null
            );
        } else {
            entity.setCondition(dto.condition());
            entity.setDateOfDiagnosis(dto.dateOfDiagnosis());
            entity.setConditionStatus(dto.conditionStatus());
            entity.setType(dto.type());
            entity.setDateOfResolution(dto.dateOfResolution());
            entity.setByPatient(dto.byPatient());
            entity.setSourceOfInformation(
                    dto.sourceOfInformation()
            );
            entity.setFreeText(null);
        }

        entity.setPatientIsFree(isFree);
    }

    private void validateRequiredFields(
            ClaimEncounterCopyPatientProblemCreateDTO dto
    ) {
        if (dto.patientIsFree() == null) {
            throw new BadRequestAlertException(
                    "Patient free flag is required.",
                    "claimEncounterCopyPatientProblem",
                    "patientIsFree.required"
            );
        }

        if (Boolean.TRUE.equals(dto.patientIsFree())) {
            if (dto.freeText() == null ||
                    dto.freeText().isBlank()) {

                throw new BadRequestAlertException(
                        "Free text is required.",
                        "claimEncounterCopyPatientProblem",
                        "freeText.required"
                );
            }

            return;
        }

        validateNormalFields(
                dto.condition(),
                dto.dateOfDiagnosis(),
                dto.conditionStatus(),
                dto.type(),
                dto.byPatient(),
                dto.sourceOfInformation()
        );
    }

    private void validateRequiredFields(
            ClaimEncounterCopyPatientProblemUpdateDTO dto
    ) {
        if (dto.patientIsFree() == null) {
            throw new BadRequestAlertException(
                    "Patient free flag is required.",
                    "claimEncounterCopyPatientProblem",
                    "patientIsFree.required"
            );
        }

        if (Boolean.TRUE.equals(dto.patientIsFree())) {
            if (dto.freeText() == null ||
                    dto.freeText().isBlank()) {

                throw new BadRequestAlertException(
                        "Free text is required.",
                        "claimEncounterCopyPatientProblem",
                        "freeText.required"
                );
            }

            return;
        }

        validateNormalFields(
                dto.condition(),
                dto.dateOfDiagnosis(),
                dto.conditionStatus(),
                dto.type(),
                dto.byPatient(),
                dto.sourceOfInformation()
        );
    }

    private void validateNormalFields(
            String condition,
            Date dateOfDiagnosis,
            String conditionStatus,
            String type,
            Boolean byPatient,
            String sourceOfInformation
    ) {
        if (condition == null || condition.isBlank()) {
            throw new BadRequestAlertException(
                    "Condition is required.",
                    "claimEncounterCopyPatientProblem",
                    "condition.required"
            );
        }

        if (dateOfDiagnosis == null) {
            throw new BadRequestAlertException(
                    "Date of diagnosis is required.",
                    "claimEncounterCopyPatientProblem",
                    "dateOfDiagnosis.required"
            );
        }

        if (conditionStatus == null || conditionStatus.isBlank()) {
            throw new BadRequestAlertException(
                    "Status is required.",
                    "claimEncounterCopyPatientProblem",
                    "status.required"
            );
        }

        if (type == null || type.isBlank()) {
            throw new BadRequestAlertException(
                    "Type is required.",
                    "claimEncounterCopyPatientProblem",
                    "type.required"
            );
        }

        if (Boolean.FALSE.equals(byPatient)
                && (sourceOfInformation == null
                || sourceOfInformation.isBlank())) {

            throw new BadRequestAlertException(
                    "Source of information is required when problem is not reported by patient.",
                    "claimEncounterCopyPatientProblem",
                    "source.required"
            );
        }
    }
}
