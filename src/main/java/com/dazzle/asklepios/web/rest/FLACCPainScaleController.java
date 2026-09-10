package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.FLACCPainScale;
import com.dazzle.asklepios.service.FLACCPainScaleService;
import com.dazzle.asklepios.service.dto.flaccPainScale.FLACCPainScaleCreateDTO;
import com.dazzle.asklepios.service.dto.flaccPainScale.FLACCPainScaleUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class FLACCPainScaleController {

    private static final Logger LOG = LoggerFactory.getLogger(FLACCPainScaleController.class);

    private final FLACCPainScaleService flaccPainScaleService;

    public FLACCPainScaleController(FLACCPainScaleService flaccPainScaleService) {
        this.flaccPainScaleService = flaccPainScaleService;
    }

    @PostMapping("/flacc-pain-scales")
    public ResponseEntity<FLACCPainScale> create(
            @RequestBody FLACCPainScaleCreateDTO flaccPainScaleCreateDTO
    ) {
        LOG.debug("REST create FLACCPainScale payload={}", flaccPainScaleCreateDTO);

        FLACCPainScale created = flaccPainScaleService.create(flaccPainScaleCreateDTO);

        return ResponseEntity.ok(created);
    }

    @PutMapping("/flacc-pain-scales")
    public ResponseEntity<FLACCPainScale> update(
            @RequestBody FLACCPainScaleUpdateDTO flaccPainScaleUpdateDTO
    ) {
        LOG.debug("REST update FLACCPainScale id={} payload={}",
                flaccPainScaleUpdateDTO.getId(), flaccPainScaleUpdateDTO);

        FLACCPainScale updated = flaccPainScaleService.update(flaccPainScaleUpdateDTO);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/flacc-pain-scales/{id}")
    public ResponseEntity<FLACCPainScale> findById(
            @PathVariable Long id
    ) {
        LOG.debug("REST get FLACCPainScale id={}", id);

        FLACCPainScale result = flaccPainScaleService.findById(id);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/flacc-pain-scales/patient/{patientId}")
    public ResponseEntity<List<FLACCPainScale>> findByPatientId(
            @PathVariable Long patientId
    ) {
        LOG.debug("REST get FLACCPainScales by patientId={}", patientId);

        List<FLACCPainScale> result =
                flaccPainScaleService.findByPatientId(patientId);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/flacc-pain-scales/encounter/{encounterId}")
    public ResponseEntity<List<FLACCPainScale>> findByEncounterId(
            @PathVariable Long encounterId,
            @RequestParam(defaultValue = "false") boolean showCancelled
    ) {
        LOG.debug("REST get FLACCPainScales by encounterId={} showCancelled={}",
                encounterId, showCancelled);

        List<FLACCPainScale> result =
                flaccPainScaleService.findByEncounterId(
                        encounterId,
                        showCancelled
                );

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/flacc-pain-scales/{id}/cancel")
    public ResponseEntity<FLACCPainScale> cancel(
            @PathVariable Long id,
            @RequestParam String cancellationReason
    ) {
        LOG.debug("REST cancel FLACCPainScale id={} cancellationReason={}",
                id, cancellationReason);

        FLACCPainScale cancelled =
                flaccPainScaleService.cancel(id, cancellationReason);

        return ResponseEntity.ok(cancelled);
    }
}
