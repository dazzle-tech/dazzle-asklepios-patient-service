package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.service.PatientServiceAndProductService;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductUpdateDTO;
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
            @Valid @RequestBody PatientServiceProductCreateDTO patientServiceProductCreateDTO
    ) {

        LOG.debug("REST create Patient Service/Product payload={}", patientServiceProductCreateDTO);

        validateBillingItem(
                patientServiceProductCreateDTO.billingItemType(),
                patientServiceProductCreateDTO.brandMedicationId(),
                patientServiceProductCreateDTO.diagnosticTestId(),
                patientServiceProductCreateDTO.serviceId(),
                patientServiceProductCreateDTO.procedureId()
        );

        PatientServiceAndProduct created = patientServiceAndProductService.create(patientServiceProductCreateDTO);

        LOG.debug("REST create Patient Service/Product response={}", created);

        return ResponseEntity
                .created(URI.create("/api/patient/patient-services-products/" + created.getId()))
                .body(created);
    }

    @GetMapping("/patient-services-products/by-encounter/{encounterId}")
    public ResponseEntity<List<PatientServiceAndProduct>> getAllByEncounter(
            @PathVariable @NotNull Long encounterId,
            @ParameterObject Pageable pageable
    ) {

        LOG.debug("REST get Patient Services & Products by encounterId={}", encounterId);

        Page<PatientServiceAndProduct> page =
                patientServiceAndProductService.findAllServicesAndProductsByEncounterId(pageable, encounterId);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/patient-services-products/by-patient/{patientId}")
    public ResponseEntity<List<PatientServiceAndProduct>> getAllByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {

        LOG.debug("REST get Patient Services & Products by patientId={}", patientId);

        Page<PatientServiceAndProduct> page =
                patientServiceAndProductService
                        .findAllServicesAndProductsByPatientId(pageable, patientId);


        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @PutMapping("/patient-services-products/{id}")
    public ResponseEntity<PatientServiceAndProduct> update(
            @PathVariable @NotNull  Long id,
            @Valid @RequestBody PatientServiceProductUpdateDTO patientServiceProductUpdateDTO
    ) {

        LOG.debug("REST update Patient Service/Product id={} payload={}", id, patientServiceProductUpdateDTO);

        if (!id.equals(patientServiceProductUpdateDTO.id())) {
            throw new BadRequestAlertException(
                    "idMismatch",
                    "patientServicesAndProducts",
                    "Path variable id does not match request body id"
            );
        }

        validateBillingItem(
                patientServiceProductUpdateDTO.billingItemType(),
                patientServiceProductUpdateDTO.brandMedicationId(),
                patientServiceProductUpdateDTO.diagnosticTestId(),
                patientServiceProductUpdateDTO.serviceId(),
                patientServiceProductUpdateDTO.procedureId()
        );

        PatientServiceAndProduct updated =
                patientServiceAndProductService.update(patientServiceProductUpdateDTO);

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/patient-services-products/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {

        LOG.debug("REST delete Patient Service/Product id={}", id);

        patientServiceAndProductService.remove(id);

        return ResponseEntity.noContent().build();
    }

    private void validateBillingItem(
            BillingItemTypes billingItemType,
            Long brandMedicationId,
            Long diagnosticTestId,
            Long serviceId,
            Long procedureId
    ) {
        if (billingItemType == null) {
            throw new BadRequestAlertException(
                    "billingItemTypeRequired",
                    "patientServicesAndProducts",
                    "Billing item type is required"
            );
        }

        switch (billingItemType) {
            case MEDICATION -> {
                if (brandMedicationId == null) {
                    throw new BadRequestAlertException(
                            "brandMedicationIdRequired",
                            "patientServicesAndProducts",
                            "Brand medication is required when billing item type is MEDICATION"
                    );
                }

                if (diagnosticTestId != null || serviceId != null || procedureId != null) {
                    throw new BadRequestAlertException(
                            "invalidReferenceCombination",
                            "patientServicesAndProducts",
                            "Only brandMedicationId is allowed for MEDICATION"
                    );
                }
            }

            case LABORATORY, RADIOLOGY, PATHOLOGY -> {
                if (diagnosticTestId == null) {
                    throw new BadRequestAlertException(
                            "diagnosticTestIdRequired",
                            "patientServicesAndProducts",
                            "Diagnostic test is required for LABORATORY/RADIOLOGY/PATHOLOGY"
                    );
                }

                if (brandMedicationId != null || serviceId != null || procedureId != null) {
                    throw new BadRequestAlertException(
                            "invalidReferenceCombination",
                            "patientServicesAndProducts",
                            "Only diagnosticTestId is allowed for diagnostic billing items"
                    );
                }
            }

            case SERVICE -> {
                if (serviceId == null) {
                    throw new BadRequestAlertException(
                            "serviceIdRequired",
                            "patientServicesAndProducts",
                            "Service is required when billing item type is SERVICE"
                    );
                }

                if (brandMedicationId != null || diagnosticTestId != null || procedureId != null) {
                    throw new BadRequestAlertException(
                            "invalidReferenceCombination",
                            "patientServicesAndProducts",
                            "Only serviceId is allowed for SERVICE"
                    );
                }
            }

            case PROCEDURE -> {
                if (procedureId == null) {
                    throw new BadRequestAlertException(
                            "procedureIdRequired",
                            "patientServicesAndProducts",
                            "Procedure is required when billing item type is PROCEDURE"
                    );
                }

                if (brandMedicationId != null || diagnosticTestId != null || serviceId != null) {
                    throw new BadRequestAlertException(
                            "invalidReferenceCombination",
                            "patientServicesAndProducts",
                            "Only procedureId is allowed for PROCEDURE"
                    );
                }
            }

            default -> throw new BadRequestAlertException(
                    "invalidBillingItemType",
                    "patientServicesAndProducts",
                    "Unsupported billing item type"
            );
        }
    }
}