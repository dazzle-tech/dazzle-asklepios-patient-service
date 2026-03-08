package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.service.VitalSignsService;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsCreateDTO;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.observations.BloodPressureResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.OxygenSaturationResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.PulseRateResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.RespiratoryRateResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.TemperatureResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.VitalSignsResponseVM;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Validated
public class VitalSignsController {

    private static final Logger LOG = LoggerFactory.getLogger(VitalSignsController.class);
    private static final String ENTITY_NAME = "vitalSigns";

    private final VitalSignsService vitalSignsService;

    @PostMapping("/vital-signs")
    public ResponseEntity<VitalSigns> create(@Valid @RequestBody VitalSignsCreateDTO dto) {
        LOG.debug("[REST][CREATE] VitalSigns payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("VitalSigns payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        VitalSigns saved = vitalSignsService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/vital-signs/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/vital-signs/{id}")
    public ResponseEntity<VitalSigns> update(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody VitalSignsUpdateDTO dto
    ) {
        LOG.debug("[REST][UPDATE] VitalSigns id={} payload={}", id, dto);

        if (dto == null) {
            throw new BadRequestAlertException("VitalSigns payload is required", ENTITY_NAME, "payload.required");
        }
        if (dto.patientId() == null) {
            throw new BadRequestAlertException("Patient id is required", ENTITY_NAME, "patient.required");
        }
        if (dto.encounterId() == null) {
            throw new BadRequestAlertException("Encounter id is required", ENTITY_NAME, "encounter.required");
        }

        return vitalSignsService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException(
                        "VitalSigns not found with id " + id,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @GetMapping("/vital-signs/latest/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<VitalSigns> findLatestByEncounterId(
            @PathVariable @NotNull Long encounterId
    ) {
        return vitalSignsService.findLatestByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/vital-signs/latest/triage/encounter/{encounterId}")
    @Transactional(readOnly = true)
    public ResponseEntity<VitalSigns> findLatestTriageByEncounter(
            @PathVariable @NotNull Long encounterId
    ) {
        return vitalSignsService.findLatestTriageByEncounterId(encounterId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

//    @GetMapping("/vital-signs/patient/{patientId}/between-dates")
//    @Transactional(readOnly = true)
//    public ResponseEntity<List<VitalSignsResponseVM>> findByPatientBetweenDates(
//            @PathVariable @NotNull Long patientId,
//            @RequestParam @NotNull Instant from,
//            @RequestParam @NotNull Instant to,
//            @ParameterObject Pageable pageable
//    ) {
//        LOG.debug("[REST][FIND BETWEEN DATES] patientId={} from={} to={}", patientId, from, to);
//
//        if (from.isAfter(to)) {
//            throw new BadRequestAlertException(
//                    "`from` must be before or equal to `to`",
//                    ENTITY_NAME,
//                    "date.range.invalid"
//            );
//        }
//
//        Page<VitalSignsResponseVM> page =
//                vitalSignsService
//                        .findVitalSignsByPatientIdBetweenDates(patientId, from, to, pageable)
//                        .map(vitalSigns -> VitalSignsResponseVM.builder()
//                                .temperature(vitalSigns.getTemperature())
//                                .pulseRate(vitalSigns.getHeartRate())
//                                .respiratoryRate(vitalSigns.getRespiratoryRate())
//                                .bloodPressureSystolic(vitalSigns.getBloodPressureSystolic())
//                                .bloodPressureDiastolic(vitalSigns.getBloodPressureDiastolic())
//                                .oxygenSaturation(vitalSigns.getOxygenSaturation())
//                                .createdAt(vitalSigns.getCreatedDate())
//                                .build()
//                        );
//
//        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
//                ServletUriComponentsBuilder.fromCurrentRequest(),
//                page
//        );
//
//        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
//    }
@GetMapping("/vital-signs/patient/{patientId}/between-dates")
@Transactional(readOnly = true)
public ResponseEntity<List<VitalSignsResponseVM>> findByPatientBetweenDates(
        @PathVariable Long patientId,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @ParameterObject Pageable pageable
) {
    LOG.debug("[REST][FIND BETWEEN DATES] patientId={} from={} to={}", patientId, from, to);

    if (from != null && to != null && from.isAfter(to)) {
        throw new BadRequestAlertException(
                "`from` must be before or equal to `to`",
                ENTITY_NAME,
                "date.range.invalid"
        );
    }

    Page<VitalSignsResponseVM> page =
            vitalSignsService
                    .findVitalSignsByPatientIdBetweenDates(patientId, from, to, pageable)
                    .map(vitalSigns -> VitalSignsResponseVM.builder()
                            .temperature(vitalSigns.getTemperature())
                            .pulseRate(vitalSigns.getHeartRate())
                            .respiratoryRate(vitalSigns.getRespiratoryRate())
                            .bloodPressureSystolic(vitalSigns.getBloodPressureSystolic())
                            .bloodPressureDiastolic(vitalSigns.getBloodPressureDiastolic())
                            .oxygenSaturation(vitalSigns.getOxygenSaturation())
                            .createdAt(vitalSigns.getCreatedDate())
                            .build()
                    );

    HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
            ServletUriComponentsBuilder.fromCurrentRequest(),
            page
    );

    return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
}
    @GetMapping("/vital-signs/patient/{patientId}/respiratory-rate/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RespiratoryRateResponseVM>> findRespiratoryRateBetweenDatesList(
            @PathVariable @NotNull Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to
    ) {
        LOG.debug("[REST][LIST RESPIRATORY RATE] patientId={} from={} to={}", patientId, from, to);

        if (from.isAfter(to)) {
            throw new BadRequestAlertException("Invalid date range", ENTITY_NAME, "date.range.invalid");
        }

        List<RespiratoryRateResponseVM> result =
                vitalSignsService
                        .findVitalSignsListByPatientBetweenDates(patientId, from, to)
                        .stream()
                        .map(vitalSigns -> RespiratoryRateResponseVM.builder()
                                .respiratoryRate(vitalSigns.getRespiratoryRate())
                                .createdAt(vitalSigns.getCreatedDate())
                                .build()
                        )
                        .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/vital-signs/patient/{patientId}/temperature/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TemperatureResponseVM>> findTemperatureBetweenDatesList(
            @PathVariable @NotNull Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to
    ) {
        LOG.debug("[REST][LIST TEMPERATURE] patientId={} from={} to={}", patientId, from, to);

        if (from.isAfter(to)) {
            throw new BadRequestAlertException("Invalid date range", ENTITY_NAME, "date.range.invalid");
        }

        List<TemperatureResponseVM> result =
                vitalSignsService
                        .findVitalSignsListByPatientBetweenDates(patientId, from, to)
                        .stream()
                        .map(vitalSigns -> TemperatureResponseVM.builder()
                                .temperature(vitalSigns.getTemperature())
                                .createdAt(vitalSigns.getCreatedDate())
                                .build()
                        )
                        .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/vital-signs/patient/{patientId}/pulse-rate/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PulseRateResponseVM>> findPulseRateBetweenDatesList(
            @PathVariable @NotNull Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to
    ) {
        LOG.debug("[REST][LIST PULSE RATE] patientId={} from={} to={}", patientId, from, to);

        if (from.isAfter(to)) {
            throw new BadRequestAlertException("Invalid date range", ENTITY_NAME, "date.range.invalid");
        }

        List<PulseRateResponseVM> result =
                vitalSignsService
                        .findVitalSignsListByPatientBetweenDates(patientId, from, to)
                        .stream()
                        .map(vitalSigns -> PulseRateResponseVM.builder()
                                .pulseRate(vitalSigns.getHeartRate())
                                .createdAt(vitalSigns.getCreatedDate())
                                .build()
                        )
                        .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/vital-signs/patient/{patientId}/oxygen-saturation/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<OxygenSaturationResponseVM>> findOxygenSaturationBetweenDatesList(
            @PathVariable @NotNull Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to
    ) {
        LOG.debug("[REST][LIST OXYGEN SATURATION] patientId={} from={} to={}", patientId, from, to);

        if (from.isAfter(to)) {
            throw new BadRequestAlertException("Invalid date range", ENTITY_NAME, "date.range.invalid");
        }

        List<OxygenSaturationResponseVM> result =
                vitalSignsService
                        .findVitalSignsListByPatientBetweenDates(patientId, from, to)
                        .stream()
                        .map(vitalSigns -> OxygenSaturationResponseVM.builder()
                                .oxygenSaturation(vitalSigns.getOxygenSaturation())
                                .createdAt(vitalSigns.getCreatedDate())
                                .build()
                        )
                        .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/vital-signs/patient/{patientId}/blood-pressure/list")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BloodPressureResponseVM>> findBloodPressureBetweenDatesList(
            @PathVariable @NotNull Long patientId,
            @RequestParam @NotNull Instant from,
            @RequestParam @NotNull Instant to
    ) {
        LOG.debug("[REST][LIST BLOOD PRESSURE] patientId={} from={} to={}", patientId, from, to);

        if (from.isAfter(to)) {
            throw new BadRequestAlertException("Invalid date range", ENTITY_NAME, "date.range.invalid");
        }

        List<BloodPressureResponseVM> result =
                vitalSignsService.findVitalSignsListByPatientBetweenDates(patientId, from, to)
                        .stream()
                        .map(vitalSigns -> BloodPressureResponseVM.builder()
                                .systolic(vitalSigns.getBloodPressureSystolic())
                                .diastolic(vitalSigns.getBloodPressureDiastolic())
                                .createdAt(vitalSigns.getCreatedDate())
                                .build())
                        .toList();

        return ResponseEntity.ok(result);
    }
}
