package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateWorkingDay;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateCreateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AvailabilityTemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateService.class);
    private static final String ENTITY_NAME = "availabilityTemplate";

    private final AvailabilityTemplateRepository availabilityTemplateRepository;

    public AvailabilityTemplateService(
            AvailabilityTemplateRepository availabilityTemplateRepository
    ) {
        this.availabilityTemplateRepository = availabilityTemplateRepository;
    }

    public AvailabilityTemplate create(AvailabilityTemplateCreateDTO dto) {
        LOG.debug("create availability template {}", dto);

        validateCreate(dto);

        AvailabilityTemplate entity = toEntityForCreate(dto);
        return availabilityTemplateRepository.save(entity);
    }

    public AvailabilityTemplate update(AvailabilityTemplateUpdateDTO availabilityTemplateUpdateDTO) {
        LOG.debug("update availability template {}", availabilityTemplateUpdateDTO);
        if (!availabilityTemplateUpdateDTO.status().equals(TemplateStatus.DRAFT)) {
            new NotFoundAlertException(
                    "Cannot update AvailabilityTemplate will a status is it not draft",
                    ENTITY_NAME,
                    "notfound"
            );
        }
        AvailabilityTemplate entity = getRequired(availabilityTemplateUpdateDTO.id());
        applyUpdate(entity, availabilityTemplateUpdateDTO);
        validateEntity(entity);

        return availabilityTemplateRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public AvailabilityTemplate getOne(Long id) {
        LOG.debug("get availability template by id={}", id);
        return availabilityTemplateRepository
                .findWithWorkingDaysById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "AvailabilityTemplate not found: " + id,
                                ENTITY_NAME,
                                "notfound"
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplate> getAll() {
        LOG.debug("get all availability templates");
        return availabilityTemplateRepository.findAllBy();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplate> getAllByFacilityAndDepartment(Long departmentId) {
        LOG.debug("get availability templates by departmentId={}", departmentId);
        Long facilityId= getFacility();
        return availabilityTemplateRepository.findAllByFacilityIdAndDepartmentId(facilityId, departmentId);
    }

    @Transactional(readOnly = true)
    public AvailabilityTemplate getLatestPublishedByDepartment(Long departmentId) {
        LOG.debug("get latest published availability template by departmentId={}", departmentId);
        return availabilityTemplateRepository
                .findFirstByDepartmentIdAndStatusOrderByVersionNoDesc(departmentId, TemplateStatus.PUBLISHED)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Published AvailabilityTemplate not found for department: " + departmentId,
                                ENTITY_NAME,
                                "notfound"
                        )
                );
    }

    public Optional<AvailabilityTemplate> toggleIsActive(Long id) {
        LOG.info("Toggling isActive for active availability template id={}", id);
        Optional<AvailabilityTemplate> updated = availabilityTemplateRepository.findById(id)
                .map(entity -> {
                    entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
                    AvailabilityTemplate saved = availabilityTemplateRepository.save(entity);
                    LOG.info("availability template id={} active status changed to {}", id, saved.getIsActive());
                    return saved;
                });
        if (updated.isEmpty()) {
            LOG.debug("Toggle isActive skipped: availability template not found for id={}", id);
        }
        return updated;
    }

    public List<AvailabilityTemplate> getAllByFacilityAndTemplateType(TemplateType templateType) {
        LOG.debug("Get availability templates by templateType={}", templateType);
        Long facilityId= getFacility();
        return availabilityTemplateRepository.findAllByFacilityIdAndTemplateType(facilityId, templateType);
    }

    public List<AvailabilityTemplate> getAllByFacilityAndTemplateName( String templateName) {
        LOG.debug("Get availability templates by templateName={}", templateName);
        Long facilityId= getFacility();
        return availabilityTemplateRepository.findAllByFacilityIdAndTemplateName(facilityId, templateName);
    }

    public List<AvailabilityTemplate> getAllParentTemplateId(Long parentTemplateId) {
        LOG.debug("Get availability templates by  parentTemplateId={}", parentTemplateId);
        return availabilityTemplateRepository.findAllByParentTemplate_Id( parentTemplateId);
    }

    public List<AvailabilityTemplate> getAllByDepartmentId(Long departmentId) {
        LOG.debug("Get availability templates by departmentId={}", departmentId);
        return availabilityTemplateRepository.findAllByDepartmentId(departmentId);
    }

    public List<AvailabilityTemplate> getAllByFacilityAndStatus(TemplateStatus status) {
        LOG.debug("Get availability templates by status={}", status);
        Long facilityId= getFacility();
        return availabilityTemplateRepository.findAllByFacilityIdAndStatus(facilityId, status);
    }

    private AvailabilityTemplate getRequired(Long id) {
        return availabilityTemplateRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "AvailabilityTemplate not found: " + id,
                                ENTITY_NAME,
                                "notfound"
                        )
                );
    }

    private Long getFacility(){

        return SecurityUtils.getCurrentUserFacility()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing mandatory claim 'tenant' in JWT."));

    }

    private AvailabilityTemplate toEntityForCreate(AvailabilityTemplateCreateDTO dto) {

        AvailabilityTemplate entity = new AvailabilityTemplate();
        entity.setFacilityId(dto.facilityId());
        entity.setDepartmentId(dto.departmentId());
        entity.setTemplateName(dto.templateName());
        entity.setTemplateType(dto.templateType());
        entity.setTemplateColor(dto.templateColor());
        entity.setStatus(dto.status());
        entity.setVersionNo(dto.versionNo());
        entity.setDurationMinutes(dto.durationMinutes());
        entity.setDefaultBufferBeforeMinutes(
                dto.defaultBufferBeforeMinutes() != null ? dto.defaultBufferBeforeMinutes() : 0
        );
        entity.setDefaultBufferAfterMinutes(
                dto.defaultBufferAfterMinutes() != null ? dto.defaultBufferAfterMinutes() : 0
        );
        entity.setParallelCapacityValue(
                dto.parallelCapacityValue() != null ? dto.parallelCapacityValue() : 0
        );
        entity.setNumberOfResourcesExpected(dto.numberOfResourcesExpected());
        entity.setRequirePractitioner(
                dto.requirePractitioner() != null ? dto.requirePractitioner() : false
        );
        entity.setRequireBilling(
                dto.requireBilling() != null ? dto.requireBilling() : false
        );
        entity.setRequirePreAssessment(
                dto.requirePreAssessment() != null ? dto.requirePreAssessment() : false
        );
        entity.setAllowPatientPortalBooking(
                dto.allowPatientPortalBooking() != null ? dto.allowPatientPortalBooking() : false
        );
        entity.setRequireConfirmation(
                dto.requireConfirmation() != null ? dto.requireConfirmation() : true
        );
        entity.setFinancialDetails(dto.financialDetails());

        if (dto.copyFromTemplateId() != null) {
            entity.setCopyFromTemplate(getRequired(dto.copyFromTemplateId()));
        }

        if (dto.parentTemplateId() != null) {
            entity.setParentTemplate(getRequired(dto.parentTemplateId()));
        }

        entity.setDefaultServiceId(dto.defaultServiceId());
        entity.setDefaultPractitionerId(dto.defaultPractitionerId());


        if (dto.workingDays() != null) {
            List<AvailabilityTemplateWorkingDay> workingDays = new ArrayList<>();
            dto.workingDays().forEach(item -> {
                AvailabilityTemplateWorkingDay workingDay = new AvailabilityTemplateWorkingDay();
                workingDay.setTemplate(entity);
                workingDay.setDayOfWeek(item.dayOfWeek());
                workingDay.setIsWorking(item.isWorking());
                workingDays.add(workingDay);
            });
            entity.setWorkingDays(workingDays);
        }

        return entity;
    }

    private void applyUpdate(AvailabilityTemplate entity, AvailabilityTemplateUpdateDTO dto) {
        if (dto.templateName() != null) entity.setTemplateName(dto.templateName());
        if (dto.templateType() != null) entity.setTemplateType(dto.templateType());
        if (dto.templateColor() != null) entity.setTemplateColor(dto.templateColor());
        if (dto.status() != null) entity.setStatus(dto.status());
        if (dto.versionNo() != null) entity.setVersionNo(dto.versionNo());
        if (dto.durationMinutes() != null) entity.setDurationMinutes(dto.durationMinutes());
        if (dto.defaultBufferBeforeMinutes() != null)
            entity.setDefaultBufferBeforeMinutes(dto.defaultBufferBeforeMinutes());
        if (dto.defaultBufferAfterMinutes() != null)
            entity.setDefaultBufferAfterMinutes(dto.defaultBufferAfterMinutes());
        if (dto.parallelCapacityValue() != null) entity.setParallelCapacityValue(dto.parallelCapacityValue());
        if (dto.numberOfResourcesExpected() != null)
            entity.setNumberOfResourcesExpected(dto.numberOfResourcesExpected());
        if (dto.requirePractitioner() != null) entity.setRequirePractitioner(dto.requirePractitioner());
        if (dto.requireBilling() != null) entity.setRequireBilling(dto.requireBilling());
        if (dto.requirePreAssessment() != null) entity.setRequirePreAssessment(dto.requirePreAssessment());
        if (dto.allowPatientPortalBooking() != null)
            entity.setAllowPatientPortalBooking(dto.allowPatientPortalBooking());
        if (dto.requireConfirmation() != null) entity.setRequireConfirmation(dto.requireConfirmation());
        if (dto.financialDetails() != null) entity.setFinancialDetails(dto.financialDetails());

        if (dto.copyFromTemplateId() != null) {
            entity.setCopyFromTemplate(getRequired(dto.copyFromTemplateId()));
        }

        if (dto.parentTemplateId() != null) {
            entity.setParentTemplate(getRequired(dto.parentTemplateId()));
        }

        if (dto.defaultServiceId() != null) {
            entity.setDefaultServiceId(dto.defaultServiceId());
        }

        if (dto.defaultPractitionerId() != null) {
            entity.setDefaultPractitionerId(dto.defaultPractitionerId());
        }

        if (dto.workingDays() != null) {
            List<AvailabilityTemplateWorkingDay> workingDays = new ArrayList<>();
            dto.workingDays().forEach(item -> {
                AvailabilityTemplateWorkingDay workingDay = new AvailabilityTemplateWorkingDay();
                workingDay.setTemplate(entity);
                workingDay.setDayOfWeek(item.dayOfWeek());
                workingDay.setIsWorking(item.isWorking());
                workingDays.add(workingDay);
            });
            entity.getWorkingDays().clear();
            entity.getWorkingDays().addAll(workingDays);
        }
    }

    private void validateCreate(AvailabilityTemplateCreateDTO dto) {
        if (Boolean.TRUE.equals(dto.requirePractitioner()) && dto.defaultPractitionerId() == null) {
            throw new NotFoundAlertException(
                    "Default practitioner is required when requirePractitioner is true",
                    ENTITY_NAME,
                    "defaultPractitionernull"
            );
        }
    }

    private void validateEntity(AvailabilityTemplate entity) {
        if (Boolean.TRUE.equals(entity.getRequirePractitioner()) && entity.getDefaultPractitionerId() == null) {
            throw new NotFoundAlertException(
                    "Default practitioner is required when requirePractitioner is true",
                    ENTITY_NAME,
                    "defaultPractitionernull"
            );
        }
    }

}