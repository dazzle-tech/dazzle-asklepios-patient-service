package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.EncounterAmbientService.RecordingFile;
import com.dazzle.asklepios.service.EncounterAmbientService;
import com.dazzle.asklepios.service.dto.encounterAmbient.EncounterAmbientCreateDTO;
import com.dazzle.asklepios.service.dto.encounterAmbient.EncounterAmbientDraftUpdateDTO;
import com.dazzle.asklepios.web.rest.vm.encounter.EncounterAmbientVM;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/patient")
public class EncounterAmbientController {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAmbientController.class);

    private final EncounterAmbientService encounterAmbientService;

    public EncounterAmbientController(EncounterAmbientService encounterAmbientService) {
        this.encounterAmbientService = encounterAmbientService;
    }

    @PostMapping(value = "/encounter-ambient", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EncounterAmbientVM> create(
        @Valid @RequestPart("data") EncounterAmbientCreateDTO data,
        @RequestPart(value = "audio", required = false) MultipartFile audio
    ) {
        LOG.debug("REST create EncounterAmbient patientId={} encounterId={}", data.patientId(), data.encounterId());
        EncounterAmbientVM created = encounterAmbientService.create(data, audio);
        return ResponseEntity.created(URI.create("/api/patient/encounter-ambient/" + created.id())).body(created);
    }

    @GetMapping("/encounter-ambient/by-encounter/{encounterId}")
    public ResponseEntity<List<EncounterAmbientVM>> findByEncounter(@PathVariable Long encounterId) {
        LOG.debug("REST list EncounterAmbient by encounter encounterId={}", encounterId);
        return ResponseEntity.ok(encounterAmbientService.findByEncounter(encounterId));
    }

    @GetMapping("/encounter-ambient/{id}")
    public ResponseEntity<EncounterAmbientVM> findOne(@PathVariable Long id) {
        LOG.debug("REST get EncounterAmbient id={}", id);
        return ResponseEntity.ok(encounterAmbientService.findOne(id));
    }

    @PutMapping("/encounter-ambient/{id}/draft")
    public ResponseEntity<EncounterAmbientVM> updateDraft(@PathVariable Long id, @Valid @RequestBody EncounterAmbientDraftUpdateDTO dto) {
        LOG.debug("REST update EncounterAmbient draft id={}", id);
        return ResponseEntity.ok(encounterAmbientService.updateDraft(id, dto.draft()));
    }

    @GetMapping("/encounter-ambient/{id}/recording")
    public ResponseEntity<byte[]> getRecording(@PathVariable Long id) {
        LOG.debug("REST get EncounterAmbient recording id={}", id);
        RecordingFile payload = encounterAmbientService.getRecording(id);
        String fileName = payload.fileName() == null || payload.fileName().isBlank() ? "ambient-recording" : payload.fileName();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (payload.contentType() != null && !payload.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(payload.contentType());
        }
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
            .body(payload.recording());
    }
}
