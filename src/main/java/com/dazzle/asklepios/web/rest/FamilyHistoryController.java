package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.FamilyHistory;
import com.dazzle.asklepios.service.FamilyHistoryService;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryCancelDTO;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.FamilyHistory.FamilyHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/patient")
public class FamilyHistoryController {

    private static final Logger LOG =
            LoggerFactory.getLogger(FamilyHistoryController.class);

    private final FamilyHistoryService familyHistoryService;

    public FamilyHistoryController(FamilyHistoryService service) {
        this.familyHistoryService = service;
    }

    @PostMapping("/family-history")
    public ResponseEntity<FamilyHistory> create(
            @Valid @RequestBody FamilyHistoryCreateDTO familyHistoryCreateDTO
    ) {
        LOG.debug("REST create FamilyHistory payload={}", familyHistoryCreateDTO);

        if (familyHistoryCreateDTO == null) {
            throw new BadRequestAlertException(
                    "Family history payload is required",
                    "familyHistory",
                    "payload.required"
            );
        }

        FamilyHistory created =
                familyHistoryService.create(familyHistoryCreateDTO);

        LOG.info("REST create FamilyHistory - created id={}", created.getId());

        return ResponseEntity
                .created(URI.create("/api/patient/family-history/" + created.getId()))
                .body(created);
    }

    @PutMapping("/family-history")
    public ResponseEntity<FamilyHistory> update(
            @Valid @RequestBody FamilyHistoryUpdateDTO familyHistoryUpdateDTO
    ) {
        LOG.debug("REST update FamilyHistory payload={}", familyHistoryUpdateDTO);

        FamilyHistory updated =
                familyHistoryService.update(familyHistoryUpdateDTO);

        LOG.info("REST update FamilyHistory - updated id={}", updated.getId());

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/family-history/cancel")
    public ResponseEntity<FamilyHistory> cancel(
            @Valid @RequestBody FamilyHistoryCancelDTO familyHistoryCancelDTO
    ) {
        LOG.debug("REST cancel FamilyHistory payload={}", familyHistoryCancelDTO);

        FamilyHistory cancelled =
                familyHistoryService.cancel(familyHistoryCancelDTO);

        LOG.info("REST cancel FamilyHistory - cancelled id={}", cancelled.getId());

        return ResponseEntity.ok(cancelled);
    }

    @DeleteMapping("/family-history/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST delete FamilyHistory id={}", id);

        familyHistoryService.delete(id);

        LOG.info("REST delete FamilyHistory - deleted id={}", id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/family-history")
    public ResponseEntity<List<FamilyHistory>> list(
            @RequestParam Long patientId,
            @RequestParam(name = "showCancelled", defaultValue = "false")
            Boolean showCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list FamilyHistory patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        Page<FamilyHistory> page =
                familyHistoryService.findByPatientId(
                        patientId,
                        showCancelled,
                        pageable
                );

        LOG.info(
                "REST list FamilyHistory - returned {} items",
                page.getContent().size()
        );

        HttpHeaders headers =
                com.dazzle.asklepios.web.rest.Helper.PaginationUtil
                        .generatePaginationHttpHeaders(
                                ServletUriComponentsBuilder.fromCurrentRequest(),
                                page
                        );

        List<FamilyHistory> body = page.getContent();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}