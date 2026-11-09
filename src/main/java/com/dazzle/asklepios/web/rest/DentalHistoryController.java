package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DentalHistory;
import com.dazzle.asklepios.service.DentalHistoryService;
import com.dazzle.asklepios.service.dto.dentalHistory.DentalHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.dentalHistory.DentalHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DentalHistoryController {

    private static final Logger LOG = LoggerFactory.getLogger(DentalHistoryController.class);

    private final DentalHistoryService dentalHistoryService;

    public DentalHistoryController(DentalHistoryService dentalHistoryService) {
        this.dentalHistoryService = dentalHistoryService;
    }

    @PostMapping("/dental-histories")
    public ResponseEntity<DentalHistory> create(@Valid @RequestBody DentalHistoryCreateDTO dto) {
        LOG.debug("REST create DentalHistory payload={}", dto);
        DentalHistory saved = dentalHistoryService.create(dto);
        return ResponseEntity
                .created(java.net.URI.create("/api/patient/dental-histories/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/dental-histories/{id}")
    public ResponseEntity<DentalHistory> update(
            @PathVariable Long id,
            @Valid @RequestBody DentalHistoryUpdateDTO dto
    ) {
        LOG.debug("REST update DentalHistory id={} payload={}", id, dto);
        if (!id.equals(dto.id())) {
            dto = new DentalHistoryUpdateDTO(id, dto.historyText());
        }
        return ResponseEntity.ok(dentalHistoryService.update(dto));
    }

    @GetMapping("/dental-histories/by-patient/{patientId}")
    public ResponseEntity<List<DentalHistory>> getAllByPatient(
            @PathVariable Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST get DentalHistory by patientId={}", patientId);
        Page<DentalHistory> page = dentalHistoryService.findAllByPatientId(patientId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}
