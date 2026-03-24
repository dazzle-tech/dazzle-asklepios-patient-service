package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.AdditionalMeasurements;
import com.dazzle.asklepios.domain.enumeration.AgeGroupType;
import com.dazzle.asklepios.service.AdditionalMeasurementsService;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsGeriatricCreateDTO;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsGeriatricUpdateDTO;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsInfantCreateDTO;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsInfantUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class AdditionalMeasurementsController {

    private static final Logger LOG = LoggerFactory.getLogger(AdditionalMeasurementsController.class);

    private static final String ENTITY_NAME = "additionalMeasurements";

    // ✅ FIX: Accept both INFANT and NEONATE for the infant endpoint
    private static final Set<AgeGroupType> INFANT_AGE_GROUPS = Set.of(
            AgeGroupType.INFANT,
            AgeGroupType.NEONATE
    );

    private final AdditionalMeasurementsService additionalMeasurementsService;

    @PostMapping("/additional-measurements/infant")
    public ResponseEntity<AdditionalMeasurements> createInfant(
            @Valid @RequestBody AdditionalMeasurementsInfantCreateDTO dto
    ) {
        LOG.debug("[REST][CREATE_INFANT] AdditionalMeasurements payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("AdditionalMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }
        if (dto.ageGroup() == null) {
            throw new BadRequestAlertException("Age group is required", ENTITY_NAME, "ageGroup.required");
        }
        // ✅ FIX: was (dto.ageGroup() != AgeGroupType.INFANT) — rejected NEONATE
        if (!INFANT_AGE_GROUPS.contains(dto.ageGroup())) {
            throw new BadRequestAlertException(
                    "Age group must be INFANT or NEONATE for this endpoint",
                    ENTITY_NAME,
                    "ageGroup.invalid"
            );
        }

        AdditionalMeasurements saved = additionalMeasurementsService.createInfant(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/additional-measurements/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/additional-measurements/infant/{id}")
    public ResponseEntity<AdditionalMeasurements> updateInfant(
            @PathVariable Long id,
            @Valid @RequestBody AdditionalMeasurementsInfantUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE_INFANT] AdditionalMeasurements id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("AdditionalMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (id == null) {
            throw new BadRequestAlertException("AdditionalMeasurements id is required", ENTITY_NAME, "id.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }
        if (dto.ageGroup() == null) {
            throw new BadRequestAlertException("Age group is required", ENTITY_NAME, "ageGroup.required");
        }
        // ✅ FIX: was (dto.ageGroup() != AgeGroupType.INFANT) — rejected NEONATE
        if (!INFANT_AGE_GROUPS.contains(dto.ageGroup())) {
            throw new BadRequestAlertException(
                    "Age group must be INFANT or NEONATE for this endpoint",
                    ENTITY_NAME,
                    "ageGroup.invalid"
            );
        }

        return additionalMeasurementsService.updateInfant(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "AdditionalMeasurements not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @PostMapping("/additional-measurements/geriatric")
    public ResponseEntity<AdditionalMeasurements> createGeriatric(
            @Valid @RequestBody AdditionalMeasurementsGeriatricCreateDTO dto
    ) {
        LOG.debug("[REST][CREATE_GERIATRIC] AdditionalMeasurements payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("AdditionalMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }
        if (dto.ageGroup() == null) {
            throw new BadRequestAlertException("Age group is required", ENTITY_NAME, "ageGroup.required");
        }
        if (dto.ageGroup() != AgeGroupType.GERIATRIC) {
            throw new BadRequestAlertException(
                    "Age group must be GERIATRIC for this endpoint",
                    ENTITY_NAME,
                    "ageGroup.invalid"
            );
        }

        AdditionalMeasurements saved = additionalMeasurementsService.createGeriatric(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/additional-measurements/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/additional-measurements/geriatric/{id}")
    public ResponseEntity<AdditionalMeasurements> updateGeriatric(
            @PathVariable Long id,
            @Valid @RequestBody AdditionalMeasurementsGeriatricUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE_GERIATRIC] AdditionalMeasurements id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("AdditionalMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (id == null) {
            throw new BadRequestAlertException("AdditionalMeasurements id is required", ENTITY_NAME, "id.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }
        if (dto.ageGroup() == null) {
            throw new BadRequestAlertException("Age group is required", ENTITY_NAME, "ageGroup.required");
        }
        if (dto.ageGroup() != AgeGroupType.GERIATRIC) {
            throw new BadRequestAlertException(
                    "Age group must be GERIATRIC for this endpoint",
                    ENTITY_NAME,
                    "ageGroup.invalid"
            );
        }

        return additionalMeasurementsService.updateGeriatric(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "AdditionalMeasurements not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/additional-measurements/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AdditionalMeasurements> findLatestByEncounterId(
            @PathVariable Long encounterId
    ) {
        if (encounterId == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return additionalMeasurementsService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}