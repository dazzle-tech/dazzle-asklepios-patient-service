package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PatientPackageOrderService;
import com.dazzle.asklepios.service.PatientPackageOrderService.DiagnosticsOrderResultVM;
import com.dazzle.asklepios.service.PatientPackageOrderService.OrderResultVM;
import com.dazzle.asklepios.service.PatientPackageService;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageDiagnosticsOrderVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageOrderVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.AssignVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.CancelVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.ResponseVM;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientPackageController {

    private final PatientPackageService patientPackageService;
    private final PatientPackageOrderService patientPackageOrderService;

    public PatientPackageController(
            PatientPackageService patientPackageService,
            PatientPackageOrderService patientPackageOrderService
    ) {
        this.patientPackageService = patientPackageService;
        this.patientPackageOrderService = patientPackageOrderService;
    }

    /** Gives a package to a patient (usually right after the visit is opened). */
    @PostMapping("/patient-packages")
    public ResponseEntity<ResponseVM> assign(@Valid @RequestBody AssignVM vm) {
        ResponseVM created = patientPackageService.assign(vm);
        return ResponseEntity.created(URI.create("/api/patient/patient-packages/" + created.id())).body(created);
    }

    @GetMapping("/patient-packages/{id}")
    public ResponseEntity<ResponseVM> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(patientPackageService.findOne(id));
    }

    /** The patient's packages, newest first; activeOnly=true keeps only those with items left to order. */
    @GetMapping("/patient-packages/by-patient/{patientId}")
    public ResponseEntity<List<ResponseVM>> byPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        return ResponseEntity.ok(patientPackageService.findByPatient(patientId, activeOnly));
    }

    @GetMapping("/patient-packages/by-encounter/{encounterId}")
    public ResponseEntity<List<ResponseVM>> byEncounter(@PathVariable Long encounterId) {
        return ResponseEntity.ok(patientPackageService.findByEncounter(encounterId));
    }

    /** Allowed only while nothing has been ordered from the package. */
    @PostMapping("/patient-packages/{id}/cancel")
    public ResponseEntity<ResponseVM> cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelVM vm) {
        return ResponseEntity.ok(patientPackageService.cancel(id, vm != null ? vm.reason() : null));
    }

    /** Orders one package item in a visit and takes the quantity from the package (one transaction). */
    @PostMapping("/patient-packages/items/{itemId}/order")
    public ResponseEntity<OrderResultVM> orderItem(
            @PathVariable Long itemId,
            @Valid @RequestBody PatientPackageOrderVM vm
    ) {
        return ResponseEntity.ok(patientPackageOrderService.order(itemId, vm));
    }

    /** Orders several package tests in one diagnostic order (submitted once). */
    @PostMapping("/patient-packages/order-diagnostics")
    public ResponseEntity<DiagnosticsOrderResultVM> orderDiagnostics(
            @Valid @RequestBody PatientPackageDiagnosticsOrderVM vm
    ) {
        return ResponseEntity.ok(patientPackageOrderService.orderDiagnostics(vm));
    }
}
