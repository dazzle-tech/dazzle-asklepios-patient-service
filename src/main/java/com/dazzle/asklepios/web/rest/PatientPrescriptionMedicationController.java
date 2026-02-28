package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.service.PatientPrescriptionMedicationService;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.patientPrescription.PrescriptionMedicationUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
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

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientPrescriptionMedicationController {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientPrescriptionMedicationController.class);
    private final PatientPrescriptionMedicationService patientPrescriptionMedicationService;

    @GetMapping("/patient-prescription-medications")
    public ResponseEntity<List<PatientPrescriptionMedication>> list(
            @RequestParam Long prescriptionHeaderId,
            Pageable pageable
    ) {
        LOG.debug("Received request to list all prescription medications");
        Page<PatientPrescriptionMedication> page = patientPrescriptionMedicationService.list(prescriptionHeaderId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/patient-prescription-medications/{id}")
    public ResponseEntity<PatientPrescriptionMedication> get(@PathVariable Long id) {
        LOG.debug("Received request to get prescription medication with id: {}", id);
        return ResponseEntity.ok(patientPrescriptionMedicationService.get(id));
    }

    @PostMapping("/patient-prescription-medications")
    public ResponseEntity<PatientPrescriptionMedication> create(@RequestBody PrescriptionMedicationCreateDTO prescriptionMedicationCreateDTO) {
        LOG.debug("Received request to create a prescription medication");
        return ResponseEntity.ok(patientPrescriptionMedicationService.create(prescriptionMedicationCreateDTO));
    }

    @PutMapping("/patient-prescription-medications/{id}")
    public ResponseEntity<PatientPrescriptionMedication> update(@PathVariable Long id, @RequestBody PrescriptionMedicationUpdateDTO prescriptionMedicationUpdateDTO) {
        LOG.debug("Received request to update a prescription medication with id: {}", id);
        return ResponseEntity.ok(patientPrescriptionMedicationService.update(id, prescriptionMedicationUpdateDTO));
    }

    @DeleteMapping("/patient-prescription-medications/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("Received request to delete a prescription medication with id: {}", id);
        patientPrescriptionMedicationService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/patient-prescription-medications/{patientId}/chronic-medications/raw")
    public ResponseEntity<List<PatientPrescriptionMedication>> getAllChronicRaw(@PathVariable Long patientId) {
        LOG.debug("getAllChronicRaw prescription for patientId ={}",patientId);

        return ResponseEntity.ok(patientPrescriptionMedicationService.listAllChronicForPatient(patientId));
    }
}
