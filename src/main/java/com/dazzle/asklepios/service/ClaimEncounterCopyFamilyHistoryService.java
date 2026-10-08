package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.ClaimEncounterCopyFamilyHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.ClaimEncounterCopyFamilyHistoryRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyFamilyHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyFamilyHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyFamilyHistoryUpdateDTO;
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
public class ClaimEncounterCopyFamilyHistoryService {

    private final ClaimEncounterCopyFamilyHistoryRepository
            claimEncounterCopyFamilyHistoryRepository;

    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "claimEncounterCopyFamilyHistory",
                        "No authenticated user"
                ));
    }

    @Transactional(readOnly = true)
    public List<ClaimEncounterCopyFamilyHistory> findByCopyId(
            Long claimEncounterCopyId,
            boolean showCancelled
    ) {
        if (showCancelled) {
            return claimEncounterCopyFamilyHistoryRepository
                    .findAllByClaimEncounterCopyId(
                            claimEncounterCopyId
                    );
        }

        return claimEncounterCopyFamilyHistoryRepository
                .findByClaimEncounterCopyIdAndStatusNot(
                        claimEncounterCopyId,
                        PatientHistoryStatus.CANCELLED
                );
    }

    public ClaimEncounterCopyFamilyHistory create(
            Long claimEncounterCopyId,
            ClaimEncounterCopyFamilyHistoryCreateDTO dto
    ) {
        validateRequiredFields(dto);

        claimEncounterCopyRepository
                .findById(claimEncounterCopyId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Claim encounter copy not found with id "
                                + claimEncounterCopyId,
                        "claimEncounterCopyFamilyHistory",
                        "copy.notfound"
                ));

        ClaimEncounterCopyFamilyHistory entity =
                new ClaimEncounterCopyFamilyHistory();

        entity.setClaimEncounterCopyId(claimEncounterCopyId);

        applyCreate(entity, dto);

        entity.setStatus(PatientHistoryStatus.ACTIVE);

        return claimEncounterCopyFamilyHistoryRepository.saveAndFlush(
                entity
        );
    }

    public ClaimEncounterCopyFamilyHistory update(
            Long id,
            ClaimEncounterCopyFamilyHistoryUpdateDTO dto
    ) {
        validateRequiredFields(dto);

        ClaimEncounterCopyFamilyHistory entity =
                claimEncounterCopyFamilyHistoryRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy family history not found with id "
                                        + id,
                                "claimEncounterCopyFamilyHistory",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled family history cannot be updated.",
                    "claimEncounterCopyFamilyHistory",
                    "cancelled"
            );
        }

        applyUpdate(entity, dto);

        return claimEncounterCopyFamilyHistoryRepository.saveAndFlush(
                entity
        );
    }

    public ClaimEncounterCopyFamilyHistory cancel(
            Long id,
            ClaimEncounterCopyFamilyHistoryCancelDTO dto
    ) {
        ClaimEncounterCopyFamilyHistory entity =
                claimEncounterCopyFamilyHistoryRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy family history not found with id "
                                        + id,
                                "claimEncounterCopyFamilyHistory",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Family history is already cancelled.",
                    "claimEncounterCopyFamilyHistory",
                    "already.cancelled"
            );
        }

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(
                dto.cancellationReason().trim()
        );

        return claimEncounterCopyFamilyHistoryRepository.saveAndFlush(
                entity
        );
    }

    private void applyCreate(
            ClaimEncounterCopyFamilyHistory entity,
            ClaimEncounterCopyFamilyHistoryCreateDTO dto
    ) {
        boolean isFree =
                Boolean.TRUE.equals(dto.patientIsFree());

        if (isFree) {
            entity.setCondition(null);
            entity.setRelation(null);
            entity.setInheritedDiseases(null);
            entity.setFreeText(dto.freeText().trim());
            entity.setPatientIsFree(true);
            return;
        }

        entity.setCondition(dto.condition().trim());
        entity.setRelation(dto.relation());
        entity.setInheritedDiseases(dto.inheritedDiseases());
        entity.setPatientIsFree(false);
        entity.setFreeText(null);
    }

    private void applyUpdate(
            ClaimEncounterCopyFamilyHistory entity,
            ClaimEncounterCopyFamilyHistoryUpdateDTO dto
    ) {
        boolean isFree =
                Boolean.TRUE.equals(dto.patientIsFree());

        if (isFree) {
            entity.setCondition(null);
            entity.setRelation(null);
            entity.setInheritedDiseases(null);
            entity.setFreeText(dto.freeText().trim());
            entity.setPatientIsFree(true);
            return;
        }

        entity.setCondition(dto.condition().trim());
        entity.setRelation(dto.relation());
        entity.setInheritedDiseases(dto.inheritedDiseases());
        entity.setPatientIsFree(false);
        entity.setFreeText(null);
    }

    private void validateRequiredFields(
            ClaimEncounterCopyFamilyHistoryCreateDTO dto
    ) {
        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.condition(),
                dto.relation()
        );
    }

    private void validateRequiredFields(
            ClaimEncounterCopyFamilyHistoryUpdateDTO dto
    ) {
        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.condition(),
                dto.relation()
        );
    }

    private void validate(
            Boolean patientIsFree,
            String freeText,
            String condition,
            Object relation
    ) {
        boolean isFree = Boolean.TRUE.equals(patientIsFree);

        if (isFree) {
            if (freeText == null || freeText.trim().isEmpty()) {
                throw new BadRequestAlertException(
                        "Free text is required.",
                        "claimEncounterCopyFamilyHistory",
                        "freeText.required"
                );
            }

            return;
        }

        if (condition == null || condition.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Condition is required.",
                    "claimEncounterCopyFamilyHistory",
                    "condition.required"
            );
        }

        if (relation == null) {
            throw new BadRequestAlertException(
                    "Relation is required.",
                    "claimEncounterCopyFamilyHistory",
                    "relation.required"
            );
        }
    }
}