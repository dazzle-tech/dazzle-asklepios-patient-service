
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.ClaimEncounterDiagnosis;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.ClaimEncounterDiagnosisLogRepository;
import com.dazzle.asklepios.repository.ClaimEncounterDiagnosisRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterDiagnosisRequest;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterDiagnosisResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
public class ClaimEncounterDiagnosisService {

    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;
    private final ClaimEncounterDiagnosisRepository claimEncounterDiagnosisRepository;
    private final ClaimEncounterDiagnosisLogRepository claimEncounterDiagnosisLogRepository;

    @Transactional(readOnly = true)
    public List<ClaimEncounterDiagnosisResponse> findByEncounterId(Long encounterId) {
        ClaimEncounterCopy copy = claimEncounterCopyRepository
                .findByEncounterId(encounterId)
                .orElse(null);

        if (copy == null) {
            return List.of();
        }

        return claimEncounterDiagnosisRepository
                .findByClaimEncounterCopyId(copy.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ClaimEncounterDiagnosisResponse create(
            Long encounterId,
            ClaimEncounterDiagnosisRequest request
    ) {
        ClaimEncounterCopy copy = claimEncounterCopyRepository
                .findByEncounterId(encounterId)
                .orElse(null);

        if (copy == null) {
            return null;
        }

        ClaimEncounterDiagnosis diagnosis = new ClaimEncounterDiagnosis();
        diagnosis.setClaimEncounterCopyId(copy.getId());
        diagnosis.setEncounterId(encounterId);
        diagnosis.setDiagnosisId(request.diagnosisId());
        diagnosis.setType(request.type());
        diagnosis.setSuspected(Boolean.TRUE.equals(request.suspected()));
        diagnosis.setMajor(Boolean.TRUE.equals(request.major()));

        try {
            return mapToResponse(
                    claimEncounterDiagnosisRepository.saveAndFlush(diagnosis)
            );
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating claim encounter diagnosis.",
                    "claimEncounterDiagnosis",
                    "db.constraint"
            );
        }
    }

    @Transactional
    public ClaimEncounterDiagnosisResponse update(
            Long id,
            ClaimEncounterDiagnosisRequest request
    ) {
        ClaimEncounterDiagnosis diagnosis = claimEncounterDiagnosisRepository
                .findById(id)
                .orElse(null);

        if (diagnosis == null) {
            return null;
        }

        diagnosis.setDiagnosisId(request.diagnosisId());
        diagnosis.setType(request.type());
        diagnosis.setSuspected(Boolean.TRUE.equals(request.suspected()));
        diagnosis.setMajor(Boolean.TRUE.equals(request.major()));

        try {
            return mapToResponse(
                    claimEncounterDiagnosisRepository.saveAndFlush(diagnosis)
            );
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating claim encounter diagnosis.",
                    "claimEncounterDiagnosis",
                    "db.constraint"
            );
        }
    }

    @Transactional
    public boolean delete(Long id) {
        ClaimEncounterDiagnosis diagnosis = claimEncounterDiagnosisRepository
                .findById(id)
                .orElse(null);

        if (diagnosis == null) {
            return false;
        }

        claimEncounterDiagnosisLogRepository
                .deleteByClaimEncounterDiagnosisId(id);

        claimEncounterDiagnosisRepository.delete(diagnosis);

        return true;
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();

        String lower = message != null ? message.toLowerCase() : "";

        if (lower.contains("ux_claim_encounter_diagnosis_primary_per_encounter")) {
            throw new BadRequestAlertException(
                    "Only one PRIMARY diagnosis is allowed per encounter.",
                    "claimEncounterDiagnosis",
                    "primary.already.exists"
            );
        }

        if (lower.contains("ux_claim_encounter_diagnosis_diag_type_flags_copy_encounter")) {
            throw new BadRequestAlertException(
                    "This diagnosis already exists for the same claim encounter with the same attributes.",
                    "claimEncounterDiagnosis",
                    "duplicate"
            );
        }

        if (lower.contains("fk_claim_encounter_diagnoses_diagnosis")) {
            throw new BadRequestAlertException(
                    "Invalid diagnosis_id (diagnosis does not exist).",
                    "claimEncounterDiagnosis",
                    "fk.diagnosis"
            );
        }

        if (lower.contains("not-null") || lower.contains("null value")) {
            throw new BadRequestAlertException(
                    "Required fields are missing.",
                    "claimEncounterDiagnosis",
                    "required.fields"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving claim encounter diagnosis.",
                "claimEncounterDiagnosis",
                "db.constraint"
        );
    }

    private ClaimEncounterDiagnosisResponse mapToResponse(
            ClaimEncounterDiagnosis diagnosis
    ) {
        return new ClaimEncounterDiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getClaimEncounterCopyId(),
                diagnosis.getEncounterId(),
                diagnosis.getDiagnosisId(),
                diagnosis.getType(),
                diagnosis.getSuspected(),
                diagnosis.getMajor()
        );
    }
}
