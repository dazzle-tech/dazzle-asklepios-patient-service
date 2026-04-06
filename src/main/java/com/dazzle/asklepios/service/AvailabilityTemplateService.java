package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateAllowedService;
import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.repository.AvailabilityTemplateAllowedServiceRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateCreateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.AvailabilityTemplateUpdateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateAllowedServices.AvailabilityTemplateAllowedServiceDTO;
import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AvailabilityTemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateService.class);
    private static final String ENTITY_NAME = "availabilityTemplate";

    private final AvailabilityTemplateRepository availabilityTemplateRepository;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;
    private final ServiceHelper serviceHelper;
    private final PractitionerHelper practitionerHelper;
    private final AvailabilityTemplateAllowedServiceRepository availabilityTemplateAllowedServiceRepository;
    private final AvailabilityTemplateIntervalRepository availabilityTemplateIntervalRepository;

    public AvailabilityTemplateService(
            AvailabilityTemplateRepository availabilityTemplateRepository,
            FacilityHelper facilityHelper,
            DepartmentHelper departmentHelper,
            ServiceHelper serviceHelper,
            PractitionerHelper practitionerHelper,
            AvailabilityTemplateAllowedServiceRepository availabilityTemplateAllowedServiceRepository,
            AvailabilityTemplateIntervalRepository availabilityTemplateIntervalRepository
    ) {
        this.availabilityTemplateRepository = availabilityTemplateRepository;
        this.facilityHelper = facilityHelper;
        this.departmentHelper = departmentHelper;
        this.serviceHelper = serviceHelper;
        this.practitionerHelper = practitionerHelper;
        this.availabilityTemplateAllowedServiceRepository = availabilityTemplateAllowedServiceRepository;
        this.availabilityTemplateIntervalRepository = availabilityTemplateIntervalRepository;
    }

    public AvailabilityTemplate create(AvailabilityTemplateCreateDTO dto) {
        LOG.debug("create availability template {}", dto);

        validateCreate(dto);
        validateReferences(
                dto.facilityId(),
                dto.departmentId(),
                dto.defaultServiceId(),
                dto.defaultPractitionerId(),
                dto.requirePractitioner()
        );

        AvailabilityTemplate entity = toEntityForCreate(dto);
        AvailabilityTemplate template = availabilityTemplateRepository.save(entity);
        List<AvailabilityTemplateAllowedService> savedAllowedServices = replaceAllowedServices(template, dto.allowedServices());

        template.setAllowedServices(savedAllowedServices);
        return template;
    }

    public AvailabilityTemplate update(AvailabilityTemplateUpdateDTO dto) {
        LOG.debug("update availability template {}", dto);

        AvailabilityTemplate entity = getRequired(dto.id());

        if (!TemplateStatus.DRAFT.equals(entity.getStatus())) {
            throw new NotFoundAlertException(
                    "Cannot update AvailabilityTemplate when status is not draft",
                    ENTITY_NAME,
                    "notdraft"
            );
        }

        applyUpdate(entity, dto);
        validateEntity(entity);
        validateReferences(
                entity.getFacilityId(),
                entity.getDepartmentId(),
                entity.getDefaultServiceId(),
                entity.getDefaultPractitionerId(),
                entity.getRequirePractitioner()
        );

        AvailabilityTemplate updated = availabilityTemplateRepository.save(entity);
        List<AvailabilityTemplateAllowedService> savedAllowedServices = replaceAllowedServices(updated, dto.allowedServices());

        updated.setAllowedServices(savedAllowedServices);

        return updated;
    }

    public void hardDelete(Long id) {
        LOG.debug("Request to hard delete AvailabilityTemplate id={}", id);

        if (!availabilityTemplateRepository.existsById(id)) {
            throw new BadRequestAlertException("Template not found with id " + id, "availabilityTemplate", "notfound");
        }

        List<AvailabilityTemplateInterval> intervals = availabilityTemplateIntervalRepository.findByTemplate_Id(id);
        for (AvailabilityTemplateInterval interval : intervals) {
            availabilityTemplateAllowedServiceRepository.deleteByInterval_Id(interval.getId());
            availabilityTemplateIntervalRepository.delete(interval);
        }

        availabilityTemplateAllowedServiceRepository.deleteByTemplate_Id(id);
        availabilityTemplateRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public AvailabilityTemplate getOne(Long id) {
        LOG.debug("get availability template by id={}", id);

        AvailabilityTemplate entity = availabilityTemplateRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "AvailabilityTemplate not found: " + id,
                                ENTITY_NAME,
                                "notfound"
                        )
                );

        initializeAllowedServices(entity);

        return entity;
    }

    @Transactional(readOnly = true)
    public Page<AvailabilityTemplate> getAll(Pageable pageable) {
        LOG.debug("get all availability templates");

        Page<AvailabilityTemplate> page = availabilityTemplateRepository.findAllBy(pageable);
        page.getContent().forEach(this::initializeAllowedServices);

        return page;
    }

    @Transactional(readOnly = true)
    public Page<AvailabilityTemplate> getAllByFacilityAndDepartment(Long departmentId, Pageable pageable) {
        LOG.debug("get availability templates by departmentId={}", departmentId);
        Long facilityId = getFacility();

        Page<AvailabilityTemplate> page =
                availabilityTemplateRepository.findAllByFacilityIdAndDepartmentIdAndTemplateType(
                        facilityId, departmentId, TemplateType.DEPARTMENT, pageable
                );

        page.getContent().forEach(this::initializeAllowedServices);
        return page;
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

    @Transactional(readOnly = true)
    public Page<AvailabilityTemplate> getAllByFacilityAndTemplateType(TemplateType templateType, Pageable pageable) {
        LOG.debug("Get availability templates by templateType={}", templateType);
        Long facilityId = getFacility();

        Page<AvailabilityTemplate> page =
                availabilityTemplateRepository.findAllByFacilityIdAndTemplateType(facilityId, templateType, pageable);

        page.getContent().forEach(this::initializeAllowedServices);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<AvailabilityTemplate> getAllByFacilityAndTemplateName(String templateName, Pageable pageable) {
        LOG.debug("Get availability templates by templateName={}", templateName);
        Long facilityId = getFacility();

        Page<AvailabilityTemplate> page =
                availabilityTemplateRepository.findAllByFacilityIdAndTemplateNameIsContainingIgnoreCaseAndTemplateType(
                        facilityId, templateName, TemplateType.DEPARTMENT, pageable
                );

        page.getContent().forEach(this::initializeAllowedServices);
        return page;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplate> getAllParentTemplateId(Long parentTemplateId) {
        LOG.debug("Get availability templates by parentTemplateId={}", parentTemplateId);

        List<AvailabilityTemplate> templates =
                availabilityTemplateRepository.findAllByParentTemplate_Id(parentTemplateId);

        templates.forEach(this::initializeAllowedServices);
        return templates;
    }

    @Transactional(readOnly = true)
    public Page<AvailabilityTemplate> getAllByDepartmentId(Long departmentId, Pageable pageable) {
        LOG.debug("Get availability templates by departmentId={}", departmentId);

        Page<AvailabilityTemplate> page =
                availabilityTemplateRepository.findAllByDepartmentIdAndTemplateType(
                        departmentId, TemplateType.DEPARTMENT, pageable
                );

        page.getContent().forEach(this::initializeAllowedServices);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<AvailabilityTemplate> getAllByFacilityAndStatus(TemplateStatus status, Pageable pageable) {
        LOG.debug("Get availability templates by status={}", status);
        Long facilityId = getFacility();

        Page<AvailabilityTemplate> page =
                availabilityTemplateRepository.findAllByFacilityIdAndStatusAndTemplateType(
                        facilityId, status, TemplateType.DEPARTMENT, pageable
                );

        page.getContent().forEach(this::initializeAllowedServices);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<AvailabilityTemplate> getAllActiveByFacilityAndStatusAndTemplateTypeDepartment(
            TemplateStatus status,
            Pageable pageable
    ) {
        LOG.debug("Get active availability templates by status={}", status);
        Long facilityId = getFacility();

        Page<AvailabilityTemplate> page =
                availabilityTemplateRepository.findAllByFacilityIdAndStatusAndIsActiveTrueAndTemplateType(
                        facilityId, status, TemplateType.DEPARTMENT, pageable
                );

        page.getContent().forEach(this::initializeAllowedServices);
        return page;
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

    private Long getFacility() {
        return SecurityUtils.getCurrentUserFacility()
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Missing mandatory claim 'tenant' in JWT."
                        )
                );
    }

    private void initializeAllowedServices(AvailabilityTemplate template) {
        List<AvailabilityTemplateAllowedService> templateAllowedServices =
                availabilityTemplateAllowedServiceRepository.findAllByTemplate_IdAndIntervalIsNull(template.getId());

        template.setAllowedServices(templateAllowedServices);
    }

    private List<AvailabilityTemplateAllowedService> replaceAllowedServices(
            AvailabilityTemplate template,
            List<AvailabilityTemplateAllowedServiceDTO> allowedServices
    ) {
        if (allowedServices == null || allowedServices.isEmpty()) {
            return List.of();
        }

        List<AvailabilityTemplateAllowedService> entities = allowedServices.stream()
                .filter(Objects::nonNull)
                .filter(dto -> dto.service() != null)
                .map(dto -> {
                    AvailabilityTemplateAllowedService entity = new AvailabilityTemplateAllowedService();
                    entity.setTemplate(template);
                    entity.setService(dto.service());
                    return entity;
                })
                .toList();

        return availabilityTemplateAllowedServiceRepository.saveAll(entities);
    }

    private AvailabilityTemplate toEntityForCreate(AvailabilityTemplateCreateDTO dto) {
        AvailabilityTemplate entity = new AvailabilityTemplate();
        entity.setFacilityId(dto.facilityId());
        entity.setDepartmentId(dto.departmentId());
        entity.setTemplateName(dto.templateName());
        entity.setTemplateType(dto.templateType());
        entity.setResourceId(dto.resourceId());
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

        validateWorkingDays(dto.workingDays());
        entity.setWorkingDays(dto.workingDays() == null ? List.of() : dto.workingDays());
        return entity;
    }

    private void applyUpdate(AvailabilityTemplate entity, AvailabilityTemplateUpdateDTO dto) {
        if (dto.templateName() != null) entity.setTemplateName(dto.templateName());
        if (dto.templateType() != null) entity.setTemplateType(dto.templateType());
        if (dto.resourceId() != null) entity.setResourceId(dto.resourceId());
        if (dto.templateColor() != null) entity.setTemplateColor(dto.templateColor());
        if (dto.status() != null) entity.setStatus(dto.status());
        if (dto.versionNo() != null) entity.setVersionNo(dto.versionNo());
        if (dto.durationMinutes() != null) entity.setDurationMinutes(dto.durationMinutes());
        if (dto.defaultBufferBeforeMinutes() != null) entity.setDefaultBufferBeforeMinutes(dto.defaultBufferBeforeMinutes());
        if (dto.defaultBufferAfterMinutes() != null) entity.setDefaultBufferAfterMinutes(dto.defaultBufferAfterMinutes());
        if (dto.parallelCapacityValue() != null) entity.setParallelCapacityValue(dto.parallelCapacityValue());
        if (dto.numberOfResourcesExpected() != null) entity.setNumberOfResourcesExpected(dto.numberOfResourcesExpected());
        if (dto.requirePractitioner() != null) entity.setRequirePractitioner(dto.requirePractitioner());
        if (dto.requireBilling() != null) entity.setRequireBilling(dto.requireBilling());
        if (dto.requirePreAssessment() != null) entity.setRequirePreAssessment(dto.requirePreAssessment());
        if (dto.allowPatientPortalBooking() != null) entity.setAllowPatientPortalBooking(dto.allowPatientPortalBooking());
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
            validateWorkingDays(dto.workingDays());
            entity.setWorkingDays(dto.workingDays());
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

        validateWorkingDays(dto.workingDays());
    }

    private void validateEntity(AvailabilityTemplate entity) {
        if (Boolean.TRUE.equals(entity.getRequirePractitioner()) && entity.getDefaultPractitionerId() == null) {
            throw new NotFoundAlertException(
                    "Default practitioner is required when requirePractitioner is true",
                    ENTITY_NAME,
                    "defaultPractitionernull"
            );
        }

        validateWorkingDays(entity.getWorkingDays());
    }

    private void validateWorkingDays(List<WorkingDayJson> workingDays) {
        if (workingDays == null || workingDays.isEmpty()) {
            return;
        }

        Set<DayOfWeek> uniqueDays = workingDays.stream()
                .map(WorkingDayJson::getDayOfWeek)
                .collect(Collectors.toSet());

        if (uniqueDays.size() != workingDays.size()) {
            throw new NotFoundAlertException(
                    "Duplicate working day entries",
                    ENTITY_NAME,
                    "duplicate_day"
            );
        }
    }

    private void validateReferences(
            Long facilityId,
            Long departmentId,
            Long serviceId,
            Long practitionerId,
            Boolean requirePractitioner
    ) {
        facilityHelper.validateFacilityExists(facilityId);
        departmentHelper.validateDepartmentExists(departmentId);

        if (serviceId != null) {
            serviceHelper.validateServiceExists(serviceId);
        }

        if (Boolean.TRUE.equals(requirePractitioner)) {
            if (practitionerId == null) {
                throw new NotFoundAlertException(
                        "Default practitioner is required when requirePractitioner is true",
                        ENTITY_NAME,
                        "defaultPractitionernull"
                );
            }
            practitionerHelper.validatePractitionerExists(practitionerId);
        } else if (practitionerId != null) {
            practitionerHelper.validatePractitionerExists(practitionerId);
        }
    }
}