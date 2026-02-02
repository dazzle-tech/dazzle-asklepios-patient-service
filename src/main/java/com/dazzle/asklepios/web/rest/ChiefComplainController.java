package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.service.ChiefComplainService;
import com.dazzle.asklepios.service.dto.chiefComplain.ChiefComplainCreateDTO;
import com.dazzle.asklepios.service.dto.chiefComplain.ChiefComplainUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/patient/")
public class ChiefComplainController {

    private static final Logger LOG = LoggerFactory.getLogger(ChiefComplainController.class);
    private static final String ENTITY_NAME = "ChiefComplain";

    private final ChiefComplainService chiefComplainService;
    private final ChiefComplainRepository chiefComplainRepository;

    public ChiefComplainController(ChiefComplainService chiefComplainService, ChiefComplainRepository chiefComplainRepository) {
        this.chiefComplainService = chiefComplainService;
        this.chiefComplainRepository = chiefComplainRepository;
    }

    /**
     * {@code POST /chief-complain} : Create a new ChiefComplain.
     *
     * <p>Upsert behavior at encounter level:</p>
     * <ul>
     *   <li>If a record already exists for {@code encounterId} -> update it (no new row)</li>
     *   <li>Otherwise -> create a new row</li>
     * </ul>
     */
    @PostMapping("/chief-complain")
    public ResponseEntity<ChiefComplain> create(@Valid @RequestBody ChiefComplainCreateDTO dto) {
        LOG.debug("REST create ChiefComplain payload={}", dto);

        Optional<ChiefComplain> existing = chiefComplainRepository.findTopByEncounterIdAndIsTriageOrderByCreatedDateDesc(dto.encounterId(), dto.isTriage());

        if (existing.isPresent()) {
            ChiefComplain current = existing.get();

            ChiefComplainUpdateDTO updateDTO = new ChiefComplainUpdateDTO(
                    current.getId(),
                    dto.chiefComplaint(),
                    dto.provocation(),
                    dto.palliation(),
                    dto.quality(),
                    dto.region(),
                    dto.severity(),
                    dto.onsetDateTime(),
                    dto.caseUnderstanding(),
                    dto.patientCondition(),
                    dto.isTriage()
            );

            ChiefComplain updated = chiefComplainService.update(updateDTO);
            return ResponseEntity.ok(updated);
        }

        ChiefComplain created = chiefComplainService.create(dto);
        return ResponseEntity
                .created(URI.create("/api/patient/chief-complain/" + created.getId()))
                .body(created);
    }

    /**
     * {@code PUT /chief-complain/{id}} : Update an existing ChiefComplain.
     */
    @PutMapping("/chief-complain/{id}")
    public ResponseEntity<ChiefComplain> update(@PathVariable Long id, @Valid @RequestBody ChiefComplainUpdateDTO dto) {
        LOG.debug("REST update ChiefComplain id={} payload={}", id, dto);
        if (dto.id() == null || !id.equals(dto.id())) {
            throw new BadRequestAlertException(
                    "Invalid id", ENTITY_NAME, "idinvalid"
            );
        }
        ChiefComplain updated = chiefComplainService.update(dto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/chief-complain/encounter/{encounterId}/latest")
    public ResponseEntity<ChiefComplain> getLatestByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get latest ChiefComplain by encounterId={}", encounterId);
        ChiefComplain latest = chiefComplainService.getOneByEncounterId(encounterId);
        return ResponseEntity.ok(latest);
    }

    /**
     * {@code GET /chief-complain/encounter/{encounterId}/latest-triage} : Get latest triage chiefComplain for encounter.
     */
    @GetMapping("/chief-complain/encounter/{encounterId}/latest-triage")
    public ResponseEntity<ChiefComplain> getLatestTriageByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST get latest triage ChiefComplain by encounterId={}", encounterId);
        return ResponseEntity.ok(chiefComplainService.getLatestTriageByEncounterId(encounterId));
    }

    @DeleteMapping("/chief-complain/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        LOG.debug("REST delete ChiefComplain Id={}", id);
        chiefComplainService.hardDelete(id);
        return ResponseEntity.noContent().build();
    }
}