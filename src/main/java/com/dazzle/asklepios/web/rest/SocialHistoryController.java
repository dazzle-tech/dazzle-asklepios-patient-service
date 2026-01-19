package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.service.SocialHistoryService;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.socialHistory.SocialHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.socialHistory.SocialHistoryResponseVM;
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

import java.net.URI;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class SocialHistoryController {

    private static final Logger LOG =
            LoggerFactory.getLogger(SocialHistoryController.class);

    private final SocialHistoryService service;

    public SocialHistoryController(SocialHistoryService service) {
        this.service = service;
    }


    @PostMapping("/social-history")
    public ResponseEntity<SocialHistoryResponseVM> create(
            @Valid @RequestBody SocialHistoryCreateDTO dto
    ) {
        LOG.debug("REST create SocialHistory payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "Social history payload is required",
                    "socialHistory",
                    "payload.required"
            );
        }

        SocialHistory created = service.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/social-history/" + created.getId()))
                .body(SocialHistoryResponseVM.ofEntity(created));
    }


    @PutMapping("/social-history")
    public ResponseEntity<SocialHistoryResponseVM> update(
            @Valid @RequestBody SocialHistoryUpdateDTO dto
    ) {
        SocialHistoryUpdateDTO sanitizedDto = sanitizeUpdateDTO(dto);
        SocialHistory updated = service.update(sanitizedDto);

        return ResponseEntity.ok(
                SocialHistoryResponseVM.ofEntity(updated)
        );
    }


    @DeleteMapping("/social-history/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/social-history")
    public ResponseEntity<List<SocialHistoryResponseVM>> list(
            @RequestParam Long patientId,
            @ParameterObject Pageable pageable
    ) {
        Page<SocialHistory> page =
                service.findByPatientId(patientId, pageable);

        HttpHeaders headers =
                com.dazzle.asklepios.web.rest.Helper.PaginationUtil
                        .generatePaginationHttpHeaders(
                                ServletUriComponentsBuilder.fromCurrentRequest(),
                                page
                        );

        List<SocialHistoryResponseVM> body =
                page.getContent()
                        .stream()
                        .map(SocialHistoryResponseVM::ofEntity)
                        .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
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