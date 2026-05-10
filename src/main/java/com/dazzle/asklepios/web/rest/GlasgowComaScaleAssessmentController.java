package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.GlasgowComaScaleAssessment;
import com.dazzle.asklepios.service.GlasgowComaScaleAssessmentService;
import com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment.GlasgowComaScaleAssessmentCancelDTO;
import com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment.GlasgowComaScaleAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment.GlasgowComaScaleAssessmentUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class GlasgowComaScaleAssessmentController {

    private static final Logger LOG = LoggerFactory.getLogger(GlasgowComaScaleAssessmentController.class);

    private final GlasgowComaScaleAssessmentService glasgowComaScaleAssessmentService;

    public GlasgowComaScaleAssessmentController(
            GlasgowComaScaleAssessmentService glasgowComaScaleAssessmentService
    ) {
        this.glasgowComaScaleAssessmentService = glasgowComaScaleAssessmentService;
    }

    @PostMapping("/glasgow-coma-scale-assessment")
    public ResponseEntity<GlasgowComaScaleAssessment> createGlasgowComaScaleAssessment(
            @Valid @RequestBody @NotNull GlasgowComaScaleAssessmentCreateDTO createDTO
    ) {
        LOG.debug("REST create GlasgowComaScaleAssessment payload={}", createDTO);

        GlasgowComaScaleAssessment createdGlasgowComaScaleAssessment =
                glasgowComaScaleAssessmentService.create(createDTO);

        return ResponseEntity
                .created(URI.create("/api/patient/glasgow-coma-scale-assessment/" + createdGlasgowComaScaleAssessment.getId()))
                .body(createdGlasgowComaScaleAssessment);
    }

    @PutMapping("/glasgow-coma-scale-assessment/{id}")
    public ResponseEntity<GlasgowComaScaleAssessment> updateGlasgowComaScaleAssessment(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody @NotNull GlasgowComaScaleAssessmentUpdateDTO updateDTO
    ) {
        LOG.debug("REST update GlasgowComaScaleAssessment id={} payload={}", id, updateDTO);
        if (!id.equals(updateDTO.id())) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id.",
                    "glasgowComaScaleAssessment",
                    "id.mismatch"
            );
        }

        GlasgowComaScaleAssessment updatedGlasgowComaScaleAssessment =
                glasgowComaScaleAssessmentService.update(id, updateDTO);

        return ResponseEntity.ok(updatedGlasgowComaScaleAssessment);
    }

    @PatchMapping("/glasgow-coma-scale-assessment/cancel")
    public ResponseEntity<GlasgowComaScaleAssessment> cancelGlasgowComaScaleAssessment(
            @Valid @RequestBody @NotNull GlasgowComaScaleAssessmentCancelDTO cancelDTO
    ) {

        LOG.debug("REST cancel GlasgowComaScaleAssessment payload={}", cancelDTO);

        GlasgowComaScaleAssessment cancelledGlasgowComaScaleAssessment =
                glasgowComaScaleAssessmentService.cancel(cancelDTO);

        return ResponseEntity.ok(cancelledGlasgowComaScaleAssessment);
    }

    @GetMapping("/glasgow-coma-scale-assessment/by-encounter/{encounterId}/active")
    public ResponseEntity<List<GlasgowComaScaleAssessment>> getActiveGlasgowComaScaleAssessmentsByEncounterId(
            @PathVariable @NotNull Long encounterId,
            @ParameterObject Pageable pageable
    ) {

        LOG.debug("REST get active GlasgowComaScaleAssessment list by encounterId={} pageable={}",
                encounterId, pageable);

        Page<GlasgowComaScaleAssessment> page =
                glasgowComaScaleAssessmentService.getActiveByEncounterId(encounterId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/glasgow-coma-scale-assessment/{id}")
    public ResponseEntity<GlasgowComaScaleAssessment> getGlasgowComaScaleAssessmentById(
            @PathVariable @NotNull Long id
    ) {
        LOG.debug("REST get GlasgowComaScaleAssessment by id={}", id);

        GlasgowComaScaleAssessment glasgowComaScaleAssessment =
                glasgowComaScaleAssessmentService.getById(id);

        return ResponseEntity.ok(glasgowComaScaleAssessment);
    }

    @GetMapping("/glasgow-coma-scale-assessment/by-encounter/{encounterId}")
    public ResponseEntity<List<GlasgowComaScaleAssessment>> getGlasgowComaScaleAssessmentsByEncounterId(
            @PathVariable @NotNull Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST get GlasgowComaScaleAssessment list by encounterId={} pageable={}",
                encounterId, pageable);

        Page<GlasgowComaScaleAssessment> page =
                glasgowComaScaleAssessmentService.getAllByEncounterId(encounterId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}