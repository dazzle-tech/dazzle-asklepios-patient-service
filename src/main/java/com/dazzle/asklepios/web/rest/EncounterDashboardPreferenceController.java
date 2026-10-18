package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.EncounterDashboardPreferenceService;
import com.dazzle.asklepios.service.dto.EncounterDashboardPreferenceSaveDTO;
import com.dazzle.asklepios.web.rest.vm.patientDashboard.EncounterDashboardPreferenceVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patient")
public class EncounterDashboardPreferenceController {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterDashboardPreferenceController.class);

    private final EncounterDashboardPreferenceService encounterDashboardPreferenceService;

    public EncounterDashboardPreferenceController(EncounterDashboardPreferenceService encounterDashboardPreferenceService) {
        this.encounterDashboardPreferenceService = encounterDashboardPreferenceService;
    }

    @GetMapping("/encounter-dashboard-preferences/{encounterId}")
    public ResponseEntity<EncounterDashboardPreferenceVM> get(@PathVariable Long encounterId) {
        LOG.debug("REST get encounter dashboard preference encounterId={}", encounterId);
        return ResponseEntity.ok(encounterDashboardPreferenceService.get(encounterId));
    }

    @PutMapping("/encounter-dashboard-preferences/{encounterId}")
    public ResponseEntity<EncounterDashboardPreferenceVM> save(
        @PathVariable Long encounterId,
        @Valid @RequestBody EncounterDashboardPreferenceSaveDTO body
    ) {
        LOG.debug("REST save encounter dashboard preference encounterId={} widgets={}", encounterId, body.widgetKeys());
        return ResponseEntity.ok(encounterDashboardPreferenceService.save(encounterId, body.widgetKeys()));
    }
}
