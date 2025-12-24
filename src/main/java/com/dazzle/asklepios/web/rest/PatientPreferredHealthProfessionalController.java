package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import com.dazzle.asklepios.service.PatientPreferredHealthProfessionalService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.pphp.PatientPreferredHealthProfessionalCreateVM;
import com.dazzle.asklepios.web.rest.vm.pphp.PatientPreferredHealthProfessionalResponseVM;
import com.dazzle.asklepios.web.rest.vm.pphp.PatientPreferredHealthProfessionalUpdateVM;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientPreferredHealthProfessionalController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPreferredHealthProfessionalController.class);

    private final PatientPreferredHealthProfessionalService service;

    public PatientPreferredHealthProfessionalController(PatientPreferredHealthProfessionalService service) {
        this.service = service;
    }


    @PostMapping("/preferred-health-professionals/patient/{patientId}")
    public ResponseEntity<PatientPreferredHealthProfessionalResponseVM> createPreferred(
            @PathVariable Long patientId,
            @Valid @RequestBody PatientPreferredHealthProfessionalCreateVM vm
    ) {
        LOG.debug("REST create PatientPreferredHealthProfessional for patientId={} payload={}", patientId, vm);

        PatientPreferredHealthProfessional toCreate = PatientPreferredHealthProfessional.builder()
                .practitionerId(vm.practitionerId())
                .facilityId(vm.facilityId())
                .networkAffiliation(vm.networkAffiliation())
                .relatedWith(vm.relatedWith())
                .build();

        PatientPreferredHealthProfessional created = service.create(patientId, toCreate);
        PatientPreferredHealthProfessionalResponseVM body = PatientPreferredHealthProfessionalResponseVM.ofEntity(created);

        return ResponseEntity
                .created(URI.create("/api/patient/preferred-health-professionals/" + created.getId()))
                .body(body);
    }

    @PutMapping("/preferred-health-professionals/{id}")
    public ResponseEntity<PatientPreferredHealthProfessionalResponseVM> updatePreferred(
            @PathVariable Long id,
            @Valid @RequestBody PatientPreferredHealthProfessionalUpdateVM vm
    ) {
        LOG.debug("REST update PatientPreferredHealthProfessional id={} payload={}", id, vm);

        if (vm.id() == null || !vm.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Invalid id",
                    "patientPreferredHealthProfessional",
                    "idinvalid"
            );
        }

        PatientPreferredHealthProfessional patch = new PatientPreferredHealthProfessional();
        patch.setId(vm.id());
        patch.setPractitionerId(vm.practitionerId());
        patch.setFacilityId(vm.facilityId());
        patch.setNetworkAffiliation(vm.networkAffiliation());
        patch.setRelatedWith(vm.relatedWith());

        PatientPreferredHealthProfessional updated = service.update(id, patch);
        PatientPreferredHealthProfessionalResponseVM body = PatientPreferredHealthProfessionalResponseVM.ofEntity(updated);

        return ResponseEntity.ok(body);
    }

    @GetMapping("/preferred-health-professionals/patient/{patientId}")
    public ResponseEntity<List<PatientPreferredHealthProfessionalResponseVM>> getPreferredByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list PatientPreferredHealthProfessional for patientId={} pageable={}", patientId, pageable);

        Page<PatientPreferredHealthProfessional> page = service.findAllByPatient(patientId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<PatientPreferredHealthProfessionalResponseVM> body = page
                .getContent()
                .stream()
                .map(PatientPreferredHealthProfessionalResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }


    @DeleteMapping("/preferred-health-professionals/{id}")
    public ResponseEntity<Void> deletePreferred(@PathVariable Long id) {
        LOG.debug("REST delete PatientPreferredHealthProfessional id={}", id);
        service.hardDelete(id);
        return ResponseEntity.noContent().build();
    }
}
