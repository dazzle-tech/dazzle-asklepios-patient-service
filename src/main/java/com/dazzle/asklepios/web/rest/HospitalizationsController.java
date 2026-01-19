package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Hospitalization;
import com.dazzle.asklepios.service.HospitalizationsService;
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

    private final HospitalizationsService service;

    public HospitalizationsController(HospitalizationsService service) {
        this.service = service;
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
        Hospitalization created = service.create(hospitalizationsCreateDTO);

        return ResponseEntity
                .created(URI.create("/api/patient/admissions/" + created.getId()))
                .body(HospitalizationsResponseVM.ofEntity(created));
    }


    @PutMapping("/hospitalizations")
    public ResponseEntity<HospitalizationsResponseVM> update(
            @Valid @RequestBody HospitalizationsUpdateDTO dto
    ) {
        Hospitalization updated = service.update(dto);

        return ResponseEntity.ok(
                HospitalizationsResponseVM.ofEntity(updated)
        );
    }


    @DeleteMapping("/hospitalizations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/hospitalizations")
    public ResponseEntity<List<HospitalizationsResponseVM>> list(
            @RequestParam Long patientId,
            @ParameterObject Pageable pageable
    ) {
        Page<Hospitalization> page =
                service.findByPatientId(patientId, pageable);

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
