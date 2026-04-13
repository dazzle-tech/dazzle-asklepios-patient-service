package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateLog;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.service.AvailabilityTemplateService;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateCreateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.availabilityTemplate.AvailabilityTemplateAllowedServiceResponseVM;
import com.dazzle.asklepios.web.rest.vm.availabilityTemplate.AvailabilityTemplateResponseVM;
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
    ) {
        LOG.debug("REST request to create AvailabilityTemplate : {}", dto);
        if (dto.parentTemplateId() != null && dto.templateType() == TemplateType.DEPARTMENT) {
            throw new BadRequestAlertException("Department should be a main template not a sub template", "AvailabilityTemplate", "templateTypeInvalid");

        }
        AvailabilityTemplate result = availabilityTemplateService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/availability-templates/" + result.getId()))
                .body(toResponseVM(result));
    }

    @PutMapping("/availability-templates/{id}")
    public ResponseEntity<AvailabilityTemplateResponseVM> updateAvailabilityTemplate(
            @PathVariable Long id,
            @Valid @RequestBody AvailabilityTemplateUpdateDTO dto
    ) {
        LOG.debug("REST request to update AvailabilityTemplate : {}, {}", id, dto);
        if (dto.parentTemplateId() != null && dto.templateType() == TemplateType.DEPARTMENT) {
            throw new BadRequestAlertException("Department should be a main template not a sub template", "AvailabilityTemplate", "templateTypeInvalid");

        }
        if (!id.equals(dto.id())) {
            throw new BadRequestAlertException("Path variable id does not match request body id", "AvailabilityTemplate", "idInvalid");
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

    @GetMapping("/availability-templates/{templateId}/logs")
    public ResponseEntity<List<AvailabilityTemplateLog>> getAvailabilityTemplateLogs(
            @PathVariable Long templateId
    ) {
        LOG.debug("REST request to get AvailabilityTemplate logs for templateId={}", templateId);

        List<AvailabilityTemplateLog> logs =
                availabilityTemplateService.getAvailabilityTemplateLogs(templateId);

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/availability-templates")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllAvailabilityTemplates(
            @RequestParam(required = false) Long departmentId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get AvailabilityTemplates, departmentId={}", departmentId);

        Page<AvailabilityTemplate> result = departmentId != null
                ? availabilityTemplateService.getAllByFacilityAndDepartment(departmentId, pageable)
                : availabilityTemplateService.getAll(pageable);

        return buildPagedResponse(result);
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
            @RequestParam TemplateType templateType,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get availability templates by templateType={}", templateType);

        Page<AvailabilityTemplate> result =
                availabilityTemplateService.getAllByFacilityAndTemplateType(templateType, pageable);

        return buildPagedResponse(result);
    }

    @GetMapping("/availability-templates/by-facility-and-name")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllByFacilityAndTemplateName(
            @RequestParam String templateName,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get availability templates by templateName={}", templateName);

        Page<AvailabilityTemplate> result =
                availabilityTemplateService.getAllByFacilityAndTemplateName(templateName, pageable);

        return buildPagedResponse(result);
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
            @RequestParam Long departmentId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get availability templates by departmentId={}", departmentId);

        Page<AvailabilityTemplate> result =
                availabilityTemplateService.getAllByDepartmentId(departmentId, pageable);

        return buildPagedResponse(result);
    }

    @GetMapping("/availability-templates/by-facility-and-status")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllByFacilityAndStatus(
            @RequestParam TemplateStatus status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get availability templates by status={}", status);

        Page<AvailabilityTemplate> result =
                availabilityTemplateService.getAllByFacilityAndStatus(status, pageable);

        return buildPagedResponse(result);
    }

    @GetMapping("/availability-templates/active/department/by-facility-and-status")
    public ResponseEntity<List<AvailabilityTemplateResponseVM>> getAllActiveByFacilityAndStatusAndTemplateTypeDepartment(
            @RequestParam TemplateStatus status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get active availability templates by status={}", status);

        Page<AvailabilityTemplate> result =
                availabilityTemplateService.getAllActiveByFacilityAndStatusAndTemplateTypeDepartment(status, pageable);

        return buildPagedResponse(result);
    }

    private ResponseEntity<List<AvailabilityTemplateResponseVM>> buildPagedResponse(Page<AvailabilityTemplate> result) {
        Page<AvailabilityTemplateResponseVM> page = result.map(this::toResponseVM);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        LOG.info("Retrieved {} availability templates", page.getNumberOfElements());

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
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
                entity.getIsActive(),
                entity.getFinancialDetails(),
                entity.getWorkingDays(),
                entity.getAllowedServices() != null
                        ? entity.getAllowedServices().stream()
                        .map(AvailabilityTemplateAllowedServiceResponseVM::ofEntity)
                        .toList()
                        : List.of(),
                entity.getResourceId()

        );
    }
}
