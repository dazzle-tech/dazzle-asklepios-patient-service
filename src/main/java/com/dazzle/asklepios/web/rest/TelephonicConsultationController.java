package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.TelephonicConsultation;
import com.dazzle.asklepios.service.TelephonicConsultationService;
import com.dazzle.asklepios.service.dto.telephonicconsultation.TelephonicConsultationCreateDTO;
import com.dazzle.asklepios.service.dto.telephonicconsultation.TelephonicConsultationUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.TelephonicConsultationResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient/telephonic-consultation")
public class TelephonicConsultationController {

    private static final Logger LOG = LoggerFactory.getLogger(TelephonicConsultationController.class);

    private final TelephonicConsultationService service;

    public TelephonicConsultationController(TelephonicConsultationService service) {
        this.service = service;
    }


    @PostMapping
    public ResponseEntity<TelephonicConsultationResponseVM> create(
            @Valid @RequestBody TelephonicConsultationCreateDTO dto
    ) {
        LOG.debug("REST create TelephonicConsultation payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "Telephonic consultation payload is required",
                    "telephonicConsultation",
                    "payload.required"
            );
        }

        TelephonicConsultation created = service.create(dto);

        return ResponseEntity
                .created(URI.create("/api/telephonic-consultation/" + created.getId()))
                .body(TelephonicConsultationResponseVM.ofEntity(created));
    }


    @PutMapping("/{id}")
    public ResponseEntity<TelephonicConsultationResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody TelephonicConsultationUpdateDTO dto
    ) {
        LOG.debug("REST update TelephonicConsultation id={} payload={}", id, dto);

        if (dto == null || dto.id() == null || !dto.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "telephonicConsultation",
                    "id.mismatch"
            );
        }

        TelephonicConsultation updated = service.update(id, dto);
        return ResponseEntity.ok(TelephonicConsultationResponseVM.ofEntity(updated));
    }


    @GetMapping("/not-cancelled/by-encounter/{encounterId}")
    public ResponseEntity<List<TelephonicConsultationResponseVM>> findNotCancelledByEncounter(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST find NOT_CANCELLED telephonic consultations encounterId={} pageable={}", encounterId, pageable);

        Page<TelephonicConsultation> page = service.findNotCancelled(encounterId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<TelephonicConsultationResponseVM> body = page.getContent().stream()
                .map(TelephonicConsultationResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/cancelled/by-encounter/{encounterId}")
    public ResponseEntity<List<TelephonicConsultationResponseVM>> findCancelledByEncounter(
            @PathVariable Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST find CANCELLED telephonic consultations encounterId={} pageable={}", encounterId, pageable);

        Page<TelephonicConsultation> page = service.findCancelled(encounterId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<TelephonicConsultationResponseVM> body = page.getContent().stream()
                .map(TelephonicConsultationResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }


    @PutMapping("/{id}/cancel")
    public ResponseEntity<TelephonicConsultationResponseVM> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Long cancelledBy
    ) {
        LOG.debug("REST cancel TelephonicConsultation id={} reason={}", id, reason);

        TelephonicConsultation cancelled = service.cancel(id, reason, cancelledBy);
        return ResponseEntity.ok(TelephonicConsultationResponseVM.ofEntity(cancelled));
    }
}
