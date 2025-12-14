package com.dazzle.asklepios.web.rest;


import com.dazzle.asklepios.domain.NextOfKin;
import com.dazzle.asklepios.service.NextOfKinService;
import com.dazzle.asklepios.service.dto.nextofkin.NextOfKinCreateDTO;
import com.dazzle.asklepios.service.dto.nextofkin.NextOfKinUpdateDTO;
import com.dazzle.asklepios.web.rest.vm.nextofkin.NextOfKinResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class NextOfKinController {

    private static final Logger LOG = LoggerFactory.getLogger(NextOfKinController.class);

    private final NextOfKinService nextOfKinService;

    public NextOfKinController(NextOfKinService nextOfKinService) {
        this.nextOfKinService = nextOfKinService;
    }

    /**
     * Add Next of Kin
     */
    @PostMapping("/next-of-kin")
    public ResponseEntity<NextOfKinResponseVM> createNextOfKin(
            @Valid @RequestBody NextOfKinCreateDTO dto
    ) {
        LOG.debug("REST create NextOfKin payload={}", dto);
        NextOfKin nok = nextOfKinService.create(dto);
        NextOfKinResponseVM response = NextOfKinResponseVM.ofEntity(nok);

        return ResponseEntity
                .created(URI.create("/api/patient/next-of-kin/" + nok.getId()))
                .body(response);
    }

    /**
     * Update Next of Kin
     */
    @PutMapping("/next-of-kin/{id}")
    public ResponseEntity<NextOfKinResponseVM> updateNextOfKin(
            @PathVariable Long id,
            @Valid @RequestBody NextOfKinUpdateDTO dto
    ) {
        LOG.debug("REST update NextOfKin id={} payload={}", id, dto);

        NextOfKin updated = nextOfKinService.update(dto);
        return ResponseEntity.ok(NextOfKinResponseVM.ofEntity(updated));
    }

    /**
     * Get all Next of Kin for a patient
     */
    @GetMapping("/{patientId}/next-of-kin")
    public ResponseEntity<List<NextOfKinResponseVM>> getNextOfKinByPatient(@PathVariable Long patientId) {
        LOG.debug("REST list NextOfKin by patientId={}", patientId);

        List<NextOfKinResponseVM> list = nextOfKinService.findByPatient(patientId).stream()
                .map(NextOfKinResponseVM::ofEntity)
                .toList();

        return ResponseEntity.ok(list);
    }

    /**
     * Hard delete Next of Kin
     */
    @DeleteMapping("/next-of-kin/{id}")
    public ResponseEntity<Void> deleteNextOfKin(@PathVariable Long id) {
        LOG.debug("REST delete NextOfKin id={}", id);
        nextOfKinService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
