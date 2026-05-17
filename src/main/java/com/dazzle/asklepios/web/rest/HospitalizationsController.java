package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Hospitalization;
import com.dazzle.asklepios.service.HospitalizationsService;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationCancelDTO;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsCreateDTO;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.Hospitalization.HospitalizationsResponseVM;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/patient")
public class HospitalizationsController {

    private static final Logger LOG =
            LoggerFactory.getLogger(HospitalizationsController.class);

    private final HospitalizationsService hospitalizationsService;

    public HospitalizationsController(HospitalizationsService service) {
        this.hospitalizationsService = service;
    }

    @PostMapping("/hospitalizations")
    public ResponseEntity<HospitalizationsResponseVM> create(
            @Valid @RequestBody HospitalizationsCreateDTO hospitalizationsCreateDTO
    ) {
        LOG.debug("REST create Hospitalization payload={}", hospitalizationsCreateDTO);

        if (hospitalizationsCreateDTO == null) {
            throw new BadRequestAlertException(
                    "Patient admission payload is required",
                    "hospitalization",
                    "payload.required"
            );
        }

        Hospitalization created =
                hospitalizationsService.create(hospitalizationsCreateDTO);

        LOG.info("REST create Hospitalization - created id={}", created.getId());

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/patient/hospitalizations/" + created.getId()
                        )
                )
                .body(HospitalizationsResponseVM.ofEntity(created));
    }

    @PutMapping("/hospitalizations")
    public ResponseEntity<HospitalizationsResponseVM> update(
            @Valid @RequestBody HospitalizationsUpdateDTO hospitalizationsUpdateDTO
    ) {
        LOG.debug("REST update Hospitalization payload={}", hospitalizationsUpdateDTO);

        Hospitalization updated =
                hospitalizationsService.update(hospitalizationsUpdateDTO);

        LOG.info("REST update Hospitalization - updated id={}", updated.getId());

        return ResponseEntity.ok(
                HospitalizationsResponseVM.ofEntity(updated)
        );
    }

    @PutMapping("/hospitalizations/cancel")
    public ResponseEntity<HospitalizationsResponseVM> cancel(
            @Valid @RequestBody HospitalizationCancelDTO hospitalizationCancelDTO
    ) {
        LOG.debug("REST cancel Hospitalization payload={}", hospitalizationCancelDTO);

        Hospitalization cancelled =
                hospitalizationsService.cancel(hospitalizationCancelDTO);

        LOG.info("REST cancel Hospitalization - cancelled id={}", cancelled.getId());

        return ResponseEntity.ok(
                HospitalizationsResponseVM.ofEntity(cancelled)
        );
    }

    @DeleteMapping("/hospitalizations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST delete Hospitalization id={}", id);

        hospitalizationsService.delete(id);

        LOG.info("REST delete Hospitalization - deleted id={}", id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hospitalizations")
    public ResponseEntity<List<HospitalizationsResponseVM>> list(
            @RequestParam Long patientId,
            @RequestParam(defaultValue = "false") boolean showCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list Hospitalization patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        Page<Hospitalization> page =
                hospitalizationsService.findByPatientId(
                        patientId,
                        showCancelled,
                        pageable
                );

        LOG.info(
                "REST list Hospitalization - returned {} items",
                page.getContent().size()
        );

        HttpHeaders headers =
                com.dazzle.asklepios.web.rest.Helper.PaginationUtil
                        .generatePaginationHttpHeaders(
                                ServletUriComponentsBuilder.fromCurrentRequest(),
                                page
                        );

        List<HospitalizationsResponseVM> body =
                page.getContent()
                        .stream()
                        .map(HospitalizationsResponseVM::ofEntity)
                        .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}