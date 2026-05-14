package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.service.SocialHistoryService;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryCancelDTO;
import java.net.URI;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class SocialHistoryController {

    private static final Logger LOG =
            LoggerFactory.getLogger(SocialHistoryController.class);

    private final SocialHistoryService socialHistoryService;

    public SocialHistoryController(SocialHistoryService socialHistoryService) {
        this.socialHistoryService = socialHistoryService;
    }

    @PostMapping("/social-history")
    public ResponseEntity<SocialHistory> create(
            @Valid @RequestBody SocialHistoryCreateDTO createDTO
    ) {
        LOG.debug("REST create SocialHistory payload={}", createDTO);

        if (createDTO == null) {
            throw new BadRequestAlertException(
                    "Social history payload is required",
                    "socialHistory",
                    "payload.required"
            );
        }

        SocialHistory created = socialHistoryService.create(createDTO);

        LOG.info("REST create SocialHistory - created id={}", created.getId());

        return ResponseEntity
                .created(URI.create("/api/patient/social-history/" + created.getId()))
                .body(created);
    }

    @PutMapping("/social-history")
    public ResponseEntity<SocialHistory> update(
            @Valid @RequestBody SocialHistoryUpdateDTO updateDTO
    ) {
        LOG.debug("REST update SocialHistory payload={}", updateDTO);

        SocialHistoryUpdateDTO sanitizedDTO = sanitizeUpdateDTO(updateDTO);

        SocialHistory updated = socialHistoryService.update(sanitizedDTO);

        LOG.info("REST update SocialHistory - updated id={}", updated.getId());

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/social-history/cancel")
    public ResponseEntity<SocialHistory> cancel(
            @Valid @RequestBody SocialHistoryCancelDTO cancelDTO
    ) {
        LOG.debug("REST cancel SocialHistory payload={}", cancelDTO);

        SocialHistory cancelled = socialHistoryService.cancel(cancelDTO);

        LOG.info("REST cancel SocialHistory - cancelled id={}", cancelled.getId());

        return ResponseEntity.ok(cancelled);
    }

    @DeleteMapping("/social-history/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {

        LOG.debug("REST delete SocialHistory id={}", id);

        socialHistoryService.delete(id);

        LOG.info("REST delete SocialHistory - deleted id={}", id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/social-history")
    public ResponseEntity<List<SocialHistory>> list(
            @RequestParam Long patientId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list SocialHistory patientId={} pageable={}", patientId, pageable);

        Page<SocialHistory> page =
                socialHistoryService.findByPatientId(patientId, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        LOG.debug("REST list SocialHistory - returning {} records",
                page.getContent().size());

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    private SocialHistoryUpdateDTO sanitizeUpdateDTO(SocialHistoryUpdateDTO dto) {

        Date smokeStartDate = dto.smokeStartDate();
        Integer cigaretteAmount = dto.cigaretteAmount();
        String cigaretteType = dto.cigaretteType();
        Date smokeQuitDate = dto.smokeQuitDate();
        String typeOfAlcohol = dto.typeOfAlcohol();
        Date alcoholSinceWhen = dto.alcoholSinceWhen();
        String route = dto.route();
        String frequency = dto.frequency();

        if (Boolean.FALSE.equals(dto.isCurrentSmoker())) {
            smokeStartDate = null;
            cigaretteAmount = null;
            cigaretteType = null;
        }

        if (Boolean.FALSE.equals(dto.isPreviousSmoker())) {
            smokeQuitDate = null;
        }

        if (Boolean.FALSE.equals(dto.alcoholConsumption())) {
            typeOfAlcohol = null;
            alcoholSinceWhen = null;
        }

        if (Boolean.FALSE.equals(dto.substanceUse())) {
            route = null;
            frequency = null;
        }

        return new SocialHistoryUpdateDTO(
                dto.id(),
                dto.patientId(),
                dto.isCurrentSmoker(),
                smokeStartDate,
                cigaretteAmount,
                cigaretteType,
                dto.isPreviousSmoker(),
                smokeQuitDate,
                dto.exposureToSecondHandSmoke(),
                dto.alcoholConsumption(),
                typeOfAlcohol,
                alcoholSinceWhen,
                dto.substanceUse(),
                route,
                frequency,
                dto.physicalLimitation(),
                dto.diagnosedEatingDisorders()
        );
    }
}
