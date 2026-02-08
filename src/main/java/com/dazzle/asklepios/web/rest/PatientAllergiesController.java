package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientAllergies;
import com.dazzle.asklepios.repository.PatientAllergiesActiveIngredientsRepository;
import com.dazzle.asklepios.service.PatientAllergiesService;
import com.dazzle.asklepios.service.dto.PatientAllergiesCreateDTO;
import com.dazzle.asklepios.service.dto.PatientAllergiesUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientAllergies.PatientAllergiesResponseVM;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
@RequestMapping("/api/patient")
public class PatientAllergiesController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientAllergiesController.class);

    private final PatientAllergiesService patientAllergiesService;
    private final PatientAllergiesActiveIngredientsRepository patientAllergiesActiveIngredientRepository;

    public PatientAllergiesController(PatientAllergiesService patientAllergiesService, PatientAllergiesActiveIngredientsRepository patientAllergiesActiveIngredientRepository) {
        this.patientAllergiesService = patientAllergiesService;
        this.patientAllergiesActiveIngredientRepository = patientAllergiesActiveIngredientRepository;
    }

    /**
     * {@code POST /patient-allergies} : Create a new PatientAllergies.
     */
    @PostMapping("/patient-allergies")
    public ResponseEntity<PatientAllergiesResponseVM> create(@Valid @RequestBody PatientAllergiesCreateDTO patientAllergyCreateDto) {
        LOG.debug("REST create Patient Allergy payload={}", patientAllergyCreateDto);

        PatientAllergies created = patientAllergiesService.create(patientAllergyCreateDto);

        PatientAllergiesResponseVM body =
                PatientAllergiesResponseVM.ofEntity(
                        created,
                        patientAllergiesActiveIngredientRepository
                );

        LOG.debug("REST create Patient Allergy response={}", body);

        return ResponseEntity
                .created(URI.create("/api/patient/patient-allergies/" + created.getId()))
                .body(body);
    }


    @GetMapping("/patient-allergies/by-patient/{patientId}")
    public ResponseEntity<List<PatientAllergiesResponseVM>> getAllAllergiesByPatient(
            @PathVariable Long patientId,
            @RequestParam(name = "showCancelled", defaultValue = "false") boolean showCancelled,
            @ParameterObject Pageable pageable) {
        LOG.debug("git all allergies by patient controller");
        Page<PatientAllergiesResponseVM> page = patientAllergiesService.findAllAllergiesByPatientId(pageable, showCancelled, patientId);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }


    @PutMapping("/patient-allergies/{id}/cancel")
    public ResponseEntity<PatientAllergiesResponseVM> cancel(
            @PathVariable Long id,
            @RequestParam @NotBlank @NotEmpty @Valid String reason
    ) {
        LOG.debug("REST cancel Patient Allergy : {}", id);
        return ResponseEntity.ok(
                patientAllergiesService.cancel(id, reason)
        );
    }

    @PutMapping("/patient-allergies/{id}/resolve")
    public ResponseEntity<PatientAllergiesResponseVM> resolve(
            @PathVariable Long id
    ) {
        LOG.debug("REST resolve PatientAllergies : {}", id);
        return ResponseEntity.ok(
                patientAllergiesService.resolve(id)
        );
    }

    @PutMapping("/patient-allergies/{id}/undo-resolve")
    public ResponseEntity<PatientAllergiesResponseVM> undoResolve(
            @PathVariable Long id
    ) {
        LOG.debug("REST undo resolve PatientAllergies : {}", id);
        return ResponseEntity.ok(
                patientAllergiesService.undoResolve(id)
        );
    }


    @PutMapping("/patient-allergies/{id}")
    public ResponseEntity<PatientAllergiesResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientAllergiesUpdateDTO patientAllergyUpdateDto) {

        LOG.debug("REST update Patient Allergy id={} payload={}", id, patientAllergyUpdateDto);

            PatientAllergies updated = patientAllergiesService.update(patientAllergyUpdateDto);

            PatientAllergiesResponseVM body =
                    PatientAllergiesResponseVM.ofEntity(
                            updated,
                            patientAllergiesActiveIngredientRepository
                    );

            return ResponseEntity.ok(body);

    }




}
