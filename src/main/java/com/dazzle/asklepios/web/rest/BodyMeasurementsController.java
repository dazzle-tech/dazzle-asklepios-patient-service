package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.service.BodyMeasurementsService;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsCreateDTO;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.observations.BodyMeasurementsResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.HeightResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.WeightResponseVM;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Validated
public class BodyMeasurementsController {

    private static final Logger LOG = LoggerFactory.getLogger(BodyMeasurementsController.class);
    private static final String ENTITY_NAME = "bodyMeasurements";

    private final BodyMeasurementsService bodyMeasurementsService;

    @PostMapping("/body-measurements")
    public ResponseEntity<BodyMeasurements> create(@Valid @RequestBody BodyMeasurementsCreateDTO dto) {
        LOG.debug("[REST][CREATE] BodyMeasurements payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("BodyMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        BodyMeasurements saved = bodyMeasurementsService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/body-measurements/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/body-measurements/{id}")
    public ResponseEntity<BodyMeasurements> update(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody BodyMeasurementsUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE] BodyMeasurements id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("BodyMeasurements payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return bodyMeasurementsService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "BodyMeasurements not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/body-measurements/latest/patient/{patientId}")
    @Transactional(readOnly = true)
    public ResponseEntity<BodyMeasurements> findLatestByPatientId(
            @PathVariable @NotNull Long patientId
    ) {
        return bodyMeasurementsService.findLatestByPatientId(patientId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "No body measurements found for patientId " + patientId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/body-measurements/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<BodyMeasurements> findLatestByEncounterId(
            @PathVariable @NotNull Long encounterId
    ) {
        return bodyMeasurementsService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }


@GetMapping("/body-measurements/patient/{patientId}")
@Transactional(readOnly = true)
public ResponseEntity<List<BodyMeasurementsResponseVM>> findBodyMeasurementsVmBetweenDates(
        @PathVariable Long patientId,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @ParameterObject Pageable pageable
) {

    if (from != null && to != null && from.isAfter(to)) {
        throw new BadRequestAlertException(
                "`from` must be before or equal to `to`",
                ENTITY_NAME,
                "date.range.invalid"
        );
    }

    Page<BodyMeasurementsResponseVM> result =
            bodyMeasurementsService
                    .findBodyMeasurementsByPatientBetweenDates(patientId, from, to, pageable)
                    .map(bodyMeasurements -> BodyMeasurementsResponseVM.builder()
                            .weight(bodyMeasurements.getWeight())
                            .height(bodyMeasurements.getHeight())
                            .createdAt(bodyMeasurements.getCreatedDate())
                            .build()
                    );

    HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
            ServletUriComponentsBuilder.fromCurrentRequest(),
            result
    );

    return new ResponseEntity<>(result.getContent(), headers, HttpStatus.OK);
}
    @GetMapping("/body-measurements/patient/{patientId}/weight/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<WeightResponseVM>> findWeightBetweenDatesList(
            @PathVariable @NotNull Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to
    ) {
        if (from.isAfter(to)) {
            throw new BadRequestAlertException(
                    "`from` must be before or equal to `to`",
                    ENTITY_NAME,
                    "date.range.invalid"
            );
        }

        List<WeightResponseVM> result =
                bodyMeasurementsService
                        .findBodyMeasurementsListByPatientBetweenDates(patientId, from, to)
                        .stream()
                        .map(bodyMeasurements -> WeightResponseVM.builder()
                                .weight(bodyMeasurements.getWeight())
                                .createdAt(bodyMeasurements.getCreatedDate())
                                .build()
                        )
                        .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/body-measurements/patient/{patientId}/height/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<HeightResponseVM>> findHeightBetweenDatesList(
            @PathVariable @NotNull Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to
    ) {
        if (from.isAfter(to)) {
            throw new BadRequestAlertException(
                    "`from` must be before or equal to `to`",
                    ENTITY_NAME,
                    "date.range.invalid"
            );
        }

        List<HeightResponseVM> result =
                bodyMeasurementsService
                        .findBodyMeasurementsListByPatientBetweenDates(patientId, from, to)
                        .stream()
                        .map(bodyMeasurements -> HeightResponseVM.builder()
                                .height(bodyMeasurements.getHeight())
                                .createdAt(bodyMeasurements.getCreatedDate())
                                .build()
                        )
                        .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/body-measurements/latest-height/patient/{patientId}")
    @Transactional(readOnly = true)
    public ResponseEntity<HeightResponseVM> findLatestHeightByPatientId(
            @PathVariable @NotNull Long patientId
    ) {
        return bodyMeasurementsService.findLatestHeightByPatientId(patientId)
                .map(bodyMeasurements -> ResponseEntity.ok(
                        HeightResponseVM.builder()
                                .height(bodyMeasurements.getHeight())
                                .createdAt(bodyMeasurements.getCreatedDate())
                                .build()
                ))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/body-measurements/latest-weight/patient/{patientId}")
    @Transactional(readOnly = true)
    public ResponseEntity<WeightResponseVM> findLatestWeightByPatientId(
            @PathVariable @NotNull Long patientId
    ) {
        return bodyMeasurementsService.findLatestWeightByPatientId(patientId)
                .map(bodyMeasurements -> ResponseEntity.ok(
                        WeightResponseVM.builder()
                                .weight(bodyMeasurements.getWeight())
                                .createdAt(bodyMeasurements.getCreatedDate())
                                .build()
                ))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
