package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.service.AvailabilityTemplateService;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateCreateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateUpdateDTO;
import com.dazzle.asklepios.web.rest.vm.availabilityTemplate.AvailabilityTemplateResponseVM;
import com.dazzle.asklepios.web.rest.vm.availabilityTemplate.AvailabilityTemplateWorkingDayResponseVM;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class AvailabilityTemplateController {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateController.class);

    private final AvailabilityTemplateService availabilityTemplateService;

    public AvailabilityTemplateController(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
    }

    @PostMapping("/availability-templates")
    public ResponseEntity<AvailabilityTemplateResponseVM> createAvailabilityTemplate(
            @Valid @RequestBody AvailabilityTemplateCreateDTO dto
    ) throws URISyntaxException {
        LOG.debug("REST request to create AvailabilityTemplate : {}", dto);

        AvailabilityTemplate result = availabilityTemplateService.create(dto);

        return ResponseEntity
                .created(new URI("/api/patient/availability-templates/" + result.getId()))
                .body(toResponseVM(result));
    }

    @PutMapping("/availability-templates/{id}")
    public ResponseEntity<AvailabilityTemplateResponseVM> updateAvailabilityTemplate(
            @PathVariable Long id,
            @Valid @RequestBody AvailabilityTemplateUpdateDTO dto
    ) {
        LOG.debug("REST request to update AvailabilityTemplate : {}, {}", id, dto);

        if (!id.equals(dto.id())) {
            throw new IllegalArgumentException("Path variable id does not match request body id");
        }

        AvailabilityTemplate result = availabilityTemplateService.update(dto);
        return ResponseEntity.ok(toResponseVM(result));
    }

    @GetMapping("/availability-templates/{id}")
    public ResponseEntity<AvailabilityTemplateResponseVM> getAvailabilityTemplate(@PathVariable Long id) {
        LOG.debug("REST request to get AvailabilityTemplate : {}", id);

        AvailabilityTemplate result = availabilityTemplateService.getOne(id);
        return ResponseEntity.ok(toResponseVM(result));
    }

    @GetMapping("/availability-templates")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllAvailabilityTemplates(
            @RequestParam(required = false) Long departmentId
    ) {
        LOG.debug("REST request to get AvailabilityTemplates, departmentId={}", departmentId);

        List<AvailabilityTemplate> result;
        if (departmentId != null) {
            result = availabilityTemplateService.getAllByFacilityAndDepartment(departmentId);
        } else {
            result = availabilityTemplateService.getAll();
        }

        return ResponseEntity.ok(result.stream().map(this::toResponseVM).toList());
    }

    @PutMapping("/availability-templates/{id}/toggle-active")
    public ResponseEntity<Void> isActiveToggleAvailabilityTemplate(@PathVariable Long id) {
        LOG.debug("REST request to toggle active AvailabilityTemplate : {}", id);

        availabilityTemplateService.toggleIsActive(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/availability-templates/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        LOG.debug("REST request to hard delete AvailabilityTemplate : {}", id);
        availabilityTemplateService.hardDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability-templates/by-facility-and-type")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllByFacilityAndTemplateType(
            @RequestParam TemplateType templateType
    ) {
        LOG.debug("REST request to get availability templates by  templateType={}", templateType);

        List<AvailabilityTemplateResponseVM> result = availabilityTemplateService
                .getAllByFacilityAndTemplateType(templateType)
                .stream()
                .map(this::toResponseVM)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/availability-templates/by-facility-and-name")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllByFacilityAndTemplateName(
            @RequestParam String templateName
    ) {
        LOG.debug("REST request to get availability templates by templateName={}", templateName);

        List<AvailabilityTemplateResponseVM> result = availabilityTemplateService
                .getAllByFacilityAndTemplateName(templateName)
                .stream()
                .map(this::toResponseVM)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/availability-templates/by-facility-and-parent")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllByFacilityAndParentTemplateId(
            @RequestParam Long parentTemplateId
    ) {
        LOG.debug("REST request to get availability templates by parentTemplateId={}", parentTemplateId);

        List<AvailabilityTemplateResponseVM> result = availabilityTemplateService
                .getAllParentTemplateId(parentTemplateId)
                .stream()
                .map(this::toResponseVM)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/availability-templates/by-department")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllByDepartmentId(
            @RequestParam Long departmentId
    ) {
        LOG.debug("REST request to get availability templates by departmentId={}", departmentId);

        List<AvailabilityTemplateResponseVM> result = availabilityTemplateService
                .getAllByDepartmentId(departmentId)
                .stream()
                .map(this::toResponseVM)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/availability-templates/by-facility-and-status")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllByFacilityAndStatus(
            @RequestParam TemplateStatus status
    ) {
        LOG.debug("REST request to get availability templates by status={}", status);

        List<AvailabilityTemplateResponseVM> result = availabilityTemplateService
                .getAllByFacilityAndStatus(status)
                .stream()
                .map(this::toResponseVM)
                .toList();

        return ResponseEntity.ok(result);
    }


    private AvailabilityTemplateResponseVM toResponseVM(AvailabilityTemplate entity) {
        return new AvailabilityTemplateResponseVM(
                entity.getId(),
                entity.getFacilityId(),
                entity.getDepartmentId(),
                entity.getTemplateName(),
                entity.getTemplateType(),
                entity.getTemplateColor(),
                entity.getStatus(),
                entity.getVersionNo(),
                entity.getCopyFromTemplate() != null ? entity.getCopyFromTemplate().getId() : null,
                entity.getParentTemplate() != null ? entity.getParentTemplate().getId() : null,
                entity.getDurationMinutes(),
                entity.getDefaultBufferBeforeMinutes(),
                entity.getDefaultBufferAfterMinutes(),
                entity.getParallelCapacityValue(),
                entity.getDefaultServiceId(),
                entity.getNumberOfResourcesExpected(),
                entity.getRequirePractitioner(),
                entity.getDefaultPractitionerId(),
                entity.getRequireBilling(),
                entity.getRequirePreAssessment(),
                entity.getAllowPatientPortalBooking(),
                entity.getRequireConfirmation(),
                entity.getFinancialDetails(),
                entity.getWorkingDays() == null
                        ? List.of()
                        : entity.getWorkingDays()
                        .stream()
                        .map(workingDay -> new AvailabilityTemplateWorkingDayResponseVM(
                                workingDay.getDayOfWeek(),
                                workingDay.getIsWorking()
                        ))
                        .toList()
        );
    }
}