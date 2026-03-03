package com.dazzle.asklepios.web.rest;


import com.dazzle.asklepios.domain.NextOfKin;
import com.dazzle.asklepios.service.NextOfKinService;
import com.dazzle.asklepios.service.dto.nextofkin.NextOfKinCreateDTO;
import com.dazzle.asklepios.service.dto.nextofkin.NextOfKinUpdateDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<NextOfKin> createNextOfKin(
            @Valid @RequestBody NextOfKinCreateDTO dto
    ) {
        LOG.debug("REST create NextOfKin payload={}", dto);
        NextOfKin nok = nextOfKinService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/next-of-kin/" + nok.getId()))
                .body(nok);
    }

    /**
     * Update Next of Kin
     */
    @PutMapping("/next-of-kin/{id}")
    public ResponseEntity<NextOfKin> updateNextOfKin(
            @PathVariable Long id,
            @Valid @RequestBody NextOfKinUpdateDTO dto
    ) {
        LOG.debug("REST update NextOfKin id={} payload={}", id, dto);

        NextOfKin updated = nextOfKinService.update(id,dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Get all Next of Kin for a patient
     */
    @GetMapping("/{patientId}/next-of-kin")
    public ResponseEntity<List<NextOfKin>> getNextOfKinByPatient(@PathVariable Long patientId) {
        LOG.debug("REST list NextOfKin by patientId={}", patientId);

        List<NextOfKin> list = nextOfKinService.findByPatient(patientId);

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
