package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopySurgicalHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopySurgicalHistoryRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySurgicalHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySurgicalHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopySurgicalHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimEncounterCopySurgicalHistoryService {

    private final ClaimEncounterCopySurgicalHistoryRepository repository;
    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;

    public List<ClaimEncounterCopySurgicalHistory> findByCopyId(
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

    public ClaimEncounterCopySurgicalHistory update(
            Long id,
            ClaimEncounterCopySurgicalHistoryUpdateDTO dto
    ) {
        ClaimEncounterCopySurgicalHistory entity =
                repository.findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy surgical history not found",
                                "claimEncounterCopySurgicalHistory",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled surgical history cannot be updated.",
                    "claimEncounterCopySurgicalHistory",
                    "cancelled"
            );
        }

        applyUpdate(entity, dto);

        return repository.saveAndFlush(entity);
    }

    public ClaimEncounterCopySurgicalHistory cancel(
            Long id,
            ClaimEncounterCopySurgicalHistoryCancelDTO dto
    ) {
        ClaimEncounterCopySurgicalHistory entity =
                repository.findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Claim encounter copy surgical history not found",
                                "claimEncounterCopySurgicalHistory",
                                "notfound"
                        ));

        if (entity.getStatus() == PatientHistoryStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Surgical history is already cancelled.",
                    "claimEncounterCopySurgicalHistory",
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

    public ClaimEncounterCopySurgicalHistory create(
            Long claimEncounterCopyId,
            ClaimEncounterCopySurgicalHistoryCreateDTO dto
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

        ClaimEncounterCopySurgicalHistory entity =
                new ClaimEncounterCopySurgicalHistory();

        entity.setClaimEncounterCopyId(claimEncounterCopyId);

        applyCreate(entity, dto);

        entity.setStatus(PatientHistoryStatus.ACTIVE);

        return repository.saveAndFlush(entity);
    }

    private void applyCreate(
            ClaimEncounterCopySurgicalHistory entity,
            ClaimEncounterCopySurgicalHistoryCreateDTO dto
    ) {
        boolean isFree = Boolean.TRUE.equals(
                dto.patientIsFree()
        );

        entity.setSurgery(
                isFree ? null : dto.surgery()
        );
        entity.setDateOfSurgery(
                isFree ? null : dto.dateOfSurgery()
        );
        entity.setFacility(
                isFree ? null : dto.facility()
        );
        entity.setAnesthesiaType(
                isFree ? null : dto.anesthesiaType()
        );
        entity.setComplications(
                isFree ? null : dto.complications()
        );
        entity.setAdverseReactionsToAnesthesia(
                isFree ? null : dto.adverseReactionsToAnesthesia()
        );
        entity.setHasImplantsOrDevices(
                isFree ? null : dto.hasImplantsOrDevices()
        );
        entity.setImplantsOrDevicesDescription(
                isFree ? null : dto.implantsOrDevicesDescription()
        );

        entity.setPatientIsFree(isFree);
        entity.setFreeText(
                isFree && dto.freeText() != null
                        ? dto.freeText().trim()
                        : null
        );
    }

    private void applyUpdate(
            ClaimEncounterCopySurgicalHistory entity,
            ClaimEncounterCopySurgicalHistoryUpdateDTO dto
    ) {
        boolean isFree = Boolean.TRUE.equals(
                dto.patientIsFree()
        );

        entity.setSurgery(
                isFree ? null : dto.surgery()
        );
        entity.setDateOfSurgery(
                isFree ? null : dto.dateOfSurgery()
        );
        entity.setFacility(
                isFree ? null : dto.facility()
        );
        entity.setAnesthesiaType(
                isFree ? null : dto.anesthesiaType()
        );
        entity.setComplications(
                isFree ? null : dto.complications()
        );
        entity.setAdverseReactionsToAnesthesia(
                isFree ? null : dto.adverseReactionsToAnesthesia()
        );
        entity.setHasImplantsOrDevices(
                isFree ? null : dto.hasImplantsOrDevices()
        );
        entity.setImplantsOrDevicesDescription(
                isFree ? null : dto.implantsOrDevicesDescription()
        );

        entity.setPatientIsFree(isFree);
        entity.setFreeText(
                isFree && dto.freeText() != null
                        ? dto.freeText().trim()
                        : null
        );
    }
}