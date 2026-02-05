package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PatientPrescriptionMedicationService;
import com.dazzle.asklepios.service.vm.PrescriptionMedicationCreateVM;
import com.dazzle.asklepios.service.vm.PrescriptionMedicationUpdateVM;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.dto.PatientPrescriptionMedicationDTO;
import lombok.RequiredArgsConstructor;
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

    private final PatientPrescriptionMedicationService service;

    @GetMapping
    public ResponseEntity<List<PatientPrescriptionMedicationDTO>> list(
            @RequestParam Long prescriptionHeaderId,
            Pageable pageable
    ) {
        Page<PatientPrescriptionMedicationDTO> page = service.list(prescriptionHeaderId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/patient-prescription-medications/{id}")
    public ResponseEntity<PatientPrescriptionMedicationDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping("/patient-prescription-medications")
    public ResponseEntity<PatientPrescriptionMedicationDTO> create(@RequestBody PrescriptionMedicationCreateVM vm) {
        return ResponseEntity.ok(service.create(vm));
    }

    @PutMapping("/patient-prescription-medications/{id}")
    public ResponseEntity<PatientPrescriptionMedicationDTO> update(@PathVariable Long id, @RequestBody PrescriptionMedicationUpdateVM vm) {
        return ResponseEntity.ok(service.update(id, vm));
    }

    @DeleteMapping("/patient-prescription-medications/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
