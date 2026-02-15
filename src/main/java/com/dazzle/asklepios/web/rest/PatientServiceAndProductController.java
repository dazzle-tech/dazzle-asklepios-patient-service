package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.service.PatientServiceAndProductService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.dto.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.web.rest.dto.PatientServiceProductUpdateDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientServiceAndProductController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceAndProductController.class);

    private final PatientServiceAndProductService patientServiceAndProductService;

    public PatientServiceAndProductController(
            PatientServiceAndProductService patientServiceAndProductService
    ) {
        this.patientServiceAndProductService = patientServiceAndProductService;
    }


    @PostMapping("/patient-services-products")
    public ResponseEntity<PatientServiceAndProduct> create(
            @Valid @RequestBody PatientServiceProductCreateDTO dto
    ) {

        LOG.debug("REST create Patient Service/Product payload={}", dto);

        PatientServiceAndProduct created = patientServiceAndProductService.create(dto);

        LOG.debug("REST create Patient Service/Product response={}", created);

        return ResponseEntity
                .created(URI.create("/api/patient/patient-services-products/" + created.getId()))
                .body(created);
    }


    @GetMapping("/patient-services-products/by-encounter/{encounterId}")
    public ResponseEntity<List<PatientServiceAndProduct>> getAllByEncounter(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {

        LOG.debug("REST get Patient Services & Products by encounterId={}", encounterId);

        Page<PatientServiceAndProduct> page =
                patientServiceAndProductService
                        .findAllServicesAndProductsByEncounterId(pageable, encounterId);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }


    @PutMapping("/patient-services-products/{id}")
    public ResponseEntity<PatientServiceAndProduct> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientServiceProductUpdateDTO dto
    ) {

        LOG.debug("REST update Patient Service/Product id={} payload={}", id, dto);

        PatientServiceAndProduct updated =
                patientServiceAndProductService.update(dto);

        return ResponseEntity.ok(updated);
    }


    @DeleteMapping("/patient-services-products/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {

        LOG.debug("REST delete Patient Service/Product id={}", id);

        patientServiceAndProductService.remove(id);

        return ResponseEntity.noContent().build();
    }

}
