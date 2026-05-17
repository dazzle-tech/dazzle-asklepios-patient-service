package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.CurrentMedication;
import com.dazzle.asklepios.service.CurrentMedicationService;
import com.dazzle.asklepios.service.dto.currentMedication.CurrentMedicationCancelDTO;
import com.dazzle.asklepios.service.dto.currentMedication.CurrentMedicationCreateDTO;
import com.dazzle.asklepios.service.dto.currentMedication.CurrentMedicationUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.CurrentMedication.CurrentMedicationResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class CurrentMedicationController {

    private static final Logger LOG =
            LoggerFactory.getLogger(CurrentMedicationController.class);

    private final CurrentMedicationService currentMedicationService;

    public CurrentMedicationController(
            CurrentMedicationService currentMedicationService
    ) {
        this.currentMedicationService = currentMedicationService;
    }

    @PostMapping("/current-medication")
    public ResponseEntity<CurrentMedicationResponseVM> create(
            @Valid @RequestBody CurrentMedicationCreateDTO createDTO
    ) {
        LOG.debug("REST create CurrentMedication payload={}", createDTO);

        if (createDTO == null) {
            throw new BadRequestAlertException(
                    "Current medication payload is required",
                    "currentMedication",
                    "payload.required"
            );
        }

        CurrentMedication created =
                currentMedicationService.create(createDTO);

        LOG.info(
                "REST create CurrentMedication - created id={}",
                created.getId()
        );

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/patient/current-medication/"
                                        + created.getId()
                        )
                )
                .body(CurrentMedicationResponseVM.ofEntity(created));
    }

    @PutMapping("/current-medication")
    public ResponseEntity<CurrentMedicationResponseVM> update(
            @Valid @RequestBody CurrentMedicationUpdateDTO updateDTO
    ) {
        LOG.debug("REST update CurrentMedication payload={}", updateDTO);

        CurrentMedication updated =
                currentMedicationService.update(updateDTO);

        LOG.info(
                "REST update CurrentMedication - updated id={}",
                updated.getId()
        );

        return ResponseEntity.ok(
                CurrentMedicationResponseVM.ofEntity(updated)
        );
    }

    @PutMapping("/current-medication/cancel")
    public ResponseEntity<CurrentMedicationResponseVM> cancel(
            @Valid @RequestBody CurrentMedicationCancelDTO currentMedicationCancelDTO
    ) {
        LOG.debug(
                "REST cancel CurrentMedication payload={}",
                currentMedicationCancelDTO
        );

        CurrentMedication cancelled =
                currentMedicationService.cancel(
                        currentMedicationCancelDTO
                );

        LOG.info(
                "REST cancel CurrentMedication - cancelled id={}",
                cancelled.getId()
        );

        return ResponseEntity.ok(
                CurrentMedicationResponseVM.ofEntity(cancelled)
        );
    }

    @DeleteMapping("/current-medication/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("REST delete CurrentMedication id={}", id);

        currentMedicationService.delete(id);

        LOG.info(
                "REST delete CurrentMedication - deleted id={}",
                id
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/current-medication")
    public ResponseEntity<List<CurrentMedicationResponseVM>> list(
            @RequestParam Long patientId,
            @RequestParam(defaultValue = "false") boolean showCancelled,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list CurrentMedication patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        Page<CurrentMedication> page =
                currentMedicationService.findByPatientId(
                        patientId,
                        showCancelled,
                        pageable
                );

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        LOG.debug(
                "REST list CurrentMedication - returning {} records",
                page.getContent().size()
        );

        List<CurrentMedicationResponseVM> body =
                page.getContent()
                        .stream()
                        .map(CurrentMedicationResponseVM::ofEntity)
                        .toList();

        return new ResponseEntity<>(
                body,
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/current-medication/exists")
    public ResponseEntity<Boolean> exists(
            @RequestParam Long patientId,
            @RequestParam Long activeIngredientId
    ) {
        LOG.debug(
                "REST check CurrentMedication exists patientId={} activeIngredientId={}",
                patientId,
                activeIngredientId
        );

        boolean exists =
                currentMedicationService.existsByPatientAndIngredient(
                        patientId,
                        activeIngredientId
                );

        return ResponseEntity.ok(exists);
    }
}