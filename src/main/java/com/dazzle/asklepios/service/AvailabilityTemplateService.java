package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateAllowedService;
import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.AvailabilityTemplateIntervalBreak;
import com.dazzle.asklepios.domain.AvailabilityTemplateLog;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.repository.AvailabilityTemplateAllowedServiceRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalBreakRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateLogRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

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
    private final AvailabilityTemplateLogRepository availabilityTemplateLogRepository;

    private final AvailabilityTemplateIntervalBreakRepository availabilityTemplateIntervalBreakRepository;


    public AvailabilityTemplateService(
            AvailabilityTemplateRepository availabilityTemplateRepository,
            FacilityHelper facilityHelper,
            DepartmentHelper departmentHelper,
            ServiceHelper serviceHelper,
            PractitionerHelper practitionerHelper,
            AvailabilityTemplateAllowedServiceRepository availabilityTemplateAllowedServiceRepository,
            AvailabilityTemplateIntervalRepository availabilityTemplateIntervalRepository,
            AvailabilityTemplateLogRepository availabilityTemplateLogRepository,
            AvailabilityTemplateIntervalBreakRepository availabilityTemplateIntervalBreakRepository
    ) {
        this.availabilityTemplateRepository = availabilityTemplateRepository;
        this.facilityHelper = facilityHelper;
        this.departmentHelper = departmentHelper;
        this.serviceHelper = serviceHelper;
        this.practitionerHelper = practitionerHelper;
        this.availabilityTemplateAllowedServiceRepository = availabilityTemplateAllowedServiceRepository;
        this.availabilityTemplateIntervalRepository = availabilityTemplateIntervalRepository;
        this.availabilityTemplateLogRepository = availabilityTemplateLogRepository;

        this.availabilityTemplateIntervalBreakRepository = availabilityTemplateIntervalBreakRepository;
    }

    public AvailabilityTemplate create(AvailabilityTemplateCreateDTO dto) {
        LOG.debug("create availability template {}", dto);
        try {
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
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            handleConstraintsOnCreateOrUpdate(constraintException);

            throw new BadRequestAlertException(
                    "db.constraint",
                    ENTITY_NAME,
                    "Database constraint violated while saving availability template (check required fields or unique constraints)."
            );
        }
    }

    public AvailabilityTemplate update(AvailabilityTemplateUpdateDTO dto) {
        LOG.debug("update availability template {}", dto);
        try {
            AvailabilityTemplate entity = getAvailabilityTemplate(dto.id());

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
            if (updated.getStatus() == TemplateStatus.PUBLISHED) {
                validateTemplateOrSubTemplateHasIntervals(updated.getId());
                publishSubTemplates(updated.getId());
            }
            return updated;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            handleConstraintsOnCreateOrUpdate(constraintException);

            throw new BadRequestAlertException(
                    "db.constraint",
                    ENTITY_NAME,
                    "Database constraint violated while saving availability template (check required fields or unique constraints)."
            );
        }
    }

    public AvailabilityTemplate cloneTemplate(Long sourceTemplateId) {
        LOG.debug("clone availability template sourceTemplateId={}", sourceTemplateId);

        AvailabilityTemplate source = getAvailabilityTemplate(sourceTemplateId);

        AvailabilityTemplate savedClone = cloneSingleTemplate(source, null, resolveCloneName(source));

        cloneResourceTemplates(source, savedClone);

        return savedClone;
    }


    public void hardDelete(Long id) {
        LOG.debug("Request to hard delete AvailabilityTemplate id={}", id);

        availabilityTemplateRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("Template not found with id " + id, "availabilityTemplate", "notfound"));

        if (availabilityTemplateRepository.existsByParentTemplate_Id(id) || availabilityTemplateRepository.existsByCopyFromTemplate_Id(id)) {
            throw new BadRequestAlertException(
                    "template.has.dependents",
                    ENTITY_NAME,
                    "Cannot hard delete a template that is referenced by other templates."
            );
        }

        availabilityTemplateLogRepository.deleteByTemplateIdOrCopyFromTemplateIdOrParentTemplateId(id, id, id);
        availabilityTemplateAllowedServiceRepository.deleteByTemplate_Id(id);
        availabilityTemplateIntervalRepository.deleteByTemplate_Id(id);
        availabilityTemplateIntervalBreakRepository.deleteByTemplate_Id(id);
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
    public Page<AvailabilityTemplate> getAllActiveByFacilityAndStatusAndTemplateTypeDepartment(TemplateStatus status, Pageable pageable) {
        LOG.debug("Get active availability templates by status={}", status);
        Long facilityId = getFacility();

        Page<AvailabilityTemplate> page =
                availabilityTemplateRepository.findAllByFacilityIdAndStatusAndIsActiveTrueAndTemplateType(
                        facilityId, status, TemplateType.DEPARTMENT, pageable
                );

        page.getContent().forEach(this::initializeAllowedServices);
        return page;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplateLog> getAvailabilityTemplateLogs(Long templateId) {
        LOG.debug("Get AvailabilityTemplate logs for templateId={}", templateId);
        return availabilityTemplateLogRepository.findAllByTemplateIdOrderByLogDateDesc(templateId);
    }

    private void validateTemplateOrSubTemplateHasIntervals(Long templateId) {
        boolean templateHasIntervals = availabilityTemplateIntervalRepository.existsByTemplate_Id(templateId);

        if (templateHasIntervals) {
            return;
        }

        List<AvailabilityTemplate> subTemplates = availabilityTemplateRepository.findAllByParentTemplateId(templateId);

        boolean anySubTemplateHasIntervals = subTemplates.stream()
                .anyMatch(subTemplate ->
                        availabilityTemplateIntervalRepository.existsByTemplate_Id(subTemplate.getId())
                );

        if (!anySubTemplateHasIntervals) {
            throw new BadRequestAlertException(
                    "template.no.intervals",
                    ENTITY_NAME,
                    "Cannot publish template because neither the template nor its sub-templates contain any intervals"
            );
        }
    }

    private void publishSubTemplates(Long parentTemplateId) {
        List<AvailabilityTemplate> subTemplates =
                availabilityTemplateRepository.findAllByParentTemplateId(parentTemplateId);

        for (AvailabilityTemplate subTemplate : subTemplates) {
            subTemplate.setStatus(TemplateStatus.PUBLISHED);
        }

        availabilityTemplateRepository.saveAll(subTemplates);
    }

    private AvailabilityTemplate getAvailabilityTemplate(Long id) {
        return availabilityTemplateRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "notfound",
                                ENTITY_NAME,
                                "AvailabilityTemplate not found: " + id
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
        List<AvailabilityTemplateAllowedService> templateAllowedServices = availabilityTemplateAllowedServiceRepository.findAllByTemplate_IdAndIntervalIsNull(template.getId());

        template.setAllowedServices(templateAllowedServices);
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
        entity.setDefaultBufferBeforeMinutes(dto.defaultBufferBeforeMinutes() != null ? dto.defaultBufferBeforeMinutes() : 0);
        entity.setDefaultBufferAfterMinutes(dto.defaultBufferAfterMinutes() != null ? dto.defaultBufferAfterMinutes() : 0);
        entity.setParallelCapacityValue(dto.parallelCapacityValue() != null ? dto.parallelCapacityValue() : 1);
        entity.setNumberOfResourcesExpected(dto.numberOfResourcesExpected());
        entity.setRequirePractitioner(dto.requirePractitioner() != null ? dto.requirePractitioner() : false);
        entity.setRequireBilling(dto.requireBilling() != null ? dto.requireBilling() : false);
        entity.setRequirePreAssessment(dto.requirePreAssessment() != null ? dto.requirePreAssessment() : false);
        entity.setAllowPatientPortalBooking(dto.allowPatientPortalBooking() != null ? dto.allowPatientPortalBooking() : false);
        entity.setRequireConfirmation(dto.requireConfirmation() != null ? dto.requireConfirmation() : true);
        entity.setFinancialDetails(dto.financialDetails());

        if (dto.copyFromTemplateId() != null) {
            entity.setCopyFromTemplate(getAvailabilityTemplate(dto.copyFromTemplateId()));
        }

        if (dto.parentTemplateId() != null) {
            entity.setParentTemplate(getAvailabilityTemplate(dto.parentTemplateId()));
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
            entity.setCopyFromTemplate(getAvailabilityTemplate(dto.copyFromTemplateId()));
        }

        if (dto.parentTemplateId() != null) {
            entity.setParentTemplate(getAvailabilityTemplate(dto.parentTemplateId()));
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

    private void validateReferences(Long facilityId, Long departmentId, Long serviceId, Long practitionerId, Boolean requirePractitioner) {
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

    private void handleConstraintsOnCreateOrUpdate(RuntimeException constraintException) {
        Throwable root = getRootCause(constraintException);
        String message = (root != null ? root.getMessage() : constraintException.getMessage());
        String lower = (message != null ? message.toLowerCase() : "");

        LOG.error("Database constraint violation while saving availability template: {}", message, constraintException);

        if (lower.contains("uk_template_name_per_department")
                || lower.contains("unique constraint")
                || lower.contains("duplicate key")
                || lower.contains("duplicate entry")) {
            throw new BadRequestAlertException(
                    "unique.template.name",
                    ENTITY_NAME,
                    "This name already exists for another template."
            );
        } else if (lower.contains("fk_template_copy")) {
            throw new BadRequestAlertException(
                    "fk.copy_from_template_id",
                    ENTITY_NAME,
                    "Invalid template reference for template."
            );
        } else if (lower.contains("fk_template_parent")) {
            throw new BadRequestAlertException(
                    "fk.parent_template_id",
                    ENTITY_NAME,
                    "Invalid template reference for template."
            );
        } else if (lower.contains("fk_template_service")) {
            throw new BadRequestAlertException(
                    "fk.default_service_id",
                    ENTITY_NAME,
                    "Invalid service reference for template."
            );
        } else if (lower.contains("fk_template_practitioner")) {
            throw new BadRequestAlertException(
                    "fk.default_practitioner_id",
                    ENTITY_NAME,
                    "Invalid practitioner reference for template."
            );
        } else if (lower.contains("fk_template_department")) {
            throw new BadRequestAlertException(
                    "fk.department_id",
                    ENTITY_NAME,
                    "Invalid department reference for template."
            );
        } else if (lower.contains("fk_template_facility")) {
            throw new BadRequestAlertException(
                    "fk.facility_id",
                    ENTITY_NAME,
                    "Invalid facility reference for template."
            );
        } else if (lower.contains("foreign key")) {
            throw new BadRequestAlertException(
                    "fk.foreign_key",
                    ENTITY_NAME,
                    "Invalid foreign key reference for template."
            );
        }
        throw new BadRequestAlertException(
                "db.constraint",
                ENTITY_NAME,
                "Database constraint violated while saving availability template (check required fields or unique constraints)."
        );
    }

    // Clone helper
    private String resolveCloneName(AvailabilityTemplate source) {
        return source.getTemplateName() + " - Copy";
    }

    private Integer resolveNextVersionNo(AvailabilityTemplate source) {
        AvailabilityTemplate originalTemplate = source.getCopyFromTemplate() != null ? source.getCopyFromTemplate() : source;

        List<AvailabilityTemplate> templateVersions = availabilityTemplateRepository.findByCopyFromTemplate_IdOrderByVersionNoDesc(originalTemplate.getId());

        Integer maxVersionNo = templateVersions.stream()
                .map(AvailabilityTemplate::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(originalTemplate.getVersionNo() == null ? 0 : originalTemplate.getVersionNo());

        return maxVersionNo + 1;
    }

    private void copyTemplateFields(AvailabilityTemplate source, AvailabilityTemplate target) {
        target.setFacilityId(source.getFacilityId());
        target.setDepartmentId(source.getDepartmentId());
        target.setTemplateType(source.getTemplateType());
        target.setResourceId(source.getResourceId());
        target.setTemplateColor(source.getTemplateColor());
        target.setDurationMinutes(source.getDurationMinutes());
        target.setDefaultBufferBeforeMinutes(source.getDefaultBufferBeforeMinutes());
        target.setDefaultBufferAfterMinutes(source.getDefaultBufferAfterMinutes());
        target.setParallelCapacityValue(source.getParallelCapacityValue());
        target.setDefaultServiceId(source.getDefaultServiceId());
        target.setNumberOfResourcesExpected(source.getNumberOfResourcesExpected());
        target.setRequirePractitioner(source.getRequirePractitioner());
        target.setDefaultPractitionerId(source.getDefaultPractitionerId());
        target.setRequireBilling(source.getRequireBilling());
        target.setRequirePreAssessment(source.getRequirePreAssessment());
        target.setAllowPatientPortalBooking(source.getAllowPatientPortalBooking());
        target.setRequireConfirmation(source.getRequireConfirmation());
        target.setFinancialDetails(source.getFinancialDetails());

        target.setWorkingDays(
                source.getWorkingDays() == null
                        ? List.of()
                        : source.getWorkingDays().stream()
                        .map(this::copyWorkingDay)
                        .toList()
        );
    }

    private WorkingDayJson copyWorkingDay(WorkingDayJson source) {
        WorkingDayJson copy = new WorkingDayJson();
        copy.setDayOfWeek(source.getDayOfWeek());
        copy.setIsWorking(source.getIsWorking());
        return copy;
    }

    private AvailabilityTemplate cloneSingleTemplate(AvailabilityTemplate source, AvailabilityTemplate parentTemplate, String templateName) {
        initializeAllowedServices(source);

        AvailabilityTemplate clone = new AvailabilityTemplate();

        copyTemplateFields(source, clone);

        clone.setTemplateName(templateName);
        clone.setStatus(TemplateStatus.DRAFT);
        clone.setVersionNo(resolveNextVersionNo(source));
        clone.setIsActive(true);
        clone.setCopyFromTemplate(source);
        clone.setParentTemplate(parentTemplate);

        validateEntity(clone);
        validateReferences(clone.getFacilityId(), clone.getDepartmentId(), clone.getDefaultServiceId(), clone.getDefaultPractitionerId(), clone.getRequirePractitioner());

        AvailabilityTemplate savedClone = availabilityTemplateRepository.save(clone);

        List<AvailabilityTemplateAllowedService> savedAllowedServices = replaceAllowedServicesByEntities(savedClone, source.getAllowedServices());

        savedClone.setAllowedServices(savedAllowedServices);

        cloneIntervals(source, savedClone);

        return savedClone;
    }

    private List<AvailabilityTemplateAllowedService> replaceAllowedServices(AvailabilityTemplate template, List<AvailabilityTemplateAllowedServiceDTO> allowedServices) {
        availabilityTemplateAllowedServiceRepository.deleteByTemplate_IdAndIntervalIsNull(template.getId());
        availabilityTemplateAllowedServiceRepository.flush();

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

        return availabilityTemplateAllowedServiceRepository.saveAllAndFlush(entities);
    }

    private List<AvailabilityTemplateAllowedService> replaceAllowedServicesByEntities(AvailabilityTemplate template, List<AvailabilityTemplateAllowedService> allowedServices) {
        List<EncounterReason> services = allowedServices == null
                ? List.of()
                : allowedServices.stream()
                .filter(Objects::nonNull)
                .map(AvailabilityTemplateAllowedService::getService)
                .filter(Objects::nonNull)
                .toList();

        return replaceAllowedServicesByServices(template, services);
    }

    private List<AvailabilityTemplateAllowedService> replaceAllowedServicesByServices(AvailabilityTemplate template, List<EncounterReason> services) {
        availabilityTemplateAllowedServiceRepository.deleteByTemplate_IdAndIntervalIsNull(template.getId());
        availabilityTemplateAllowedServiceRepository.flush();

        if (services == null || services.isEmpty()) {
            return List.of();
        }

        List<AvailabilityTemplateAllowedService> entities = services.stream()
                .filter(Objects::nonNull)
                .map(service -> {
                    AvailabilityTemplateAllowedService entity =
                            new AvailabilityTemplateAllowedService();

                    entity.setTemplate(template);
                    entity.setService(service);

                    return entity;
                })
                .toList();

        return availabilityTemplateAllowedServiceRepository.saveAllAndFlush(entities);
    }

    private void cloneResourceTemplates(AvailabilityTemplate sourceTemplate, AvailabilityTemplate clonedTemplate) {
        List<AvailabilityTemplate> sourceResources = availabilityTemplateRepository.findAllByParentTemplateId(sourceTemplate.getId());

        if (sourceResources == null || sourceResources.isEmpty()) {
            return;
        }

        for (AvailabilityTemplate sourceResource : sourceResources) {
            cloneSingleTemplate(sourceResource, clonedTemplate, resolveCloneName(sourceResource));
        }
    }

    private void cloneIntervals(AvailabilityTemplate source, AvailabilityTemplate target) {
        List<AvailabilityTemplateInterval> sourceIntervals = availabilityTemplateIntervalRepository.findByTemplate_Id(source.getId());

        if (sourceIntervals == null || sourceIntervals.isEmpty()) {
            return;
        }

        for (AvailabilityTemplateInterval sourceInterval : sourceIntervals) {

            AvailabilityTemplateInterval clonedInterval = new AvailabilityTemplateInterval();

            clonedInterval.setTemplate(target);
            clonedInterval.setDayOfWeek(sourceInterval.getDayOfWeek());
            clonedInterval.setStartTime(sourceInterval.getStartTime());
            clonedInterval.setEndTime(sourceInterval.getEndTime());
            clonedInterval.setSlotStrategy(sourceInterval.getSlotStrategy());
            clonedInterval.setSlotDurationMinutes(sourceInterval.getSlotDurationMinutes());

            AvailabilityTemplateInterval savedInterval = availabilityTemplateIntervalRepository.save(clonedInterval);

            cloneIntervalBreaks(sourceInterval, savedInterval);
            cloneIntervalAllowedServices(sourceInterval, savedInterval, target);
        }
    }

    private void cloneIntervalBreaks(AvailabilityTemplateInterval sourceInterval, AvailabilityTemplateInterval targetInterval) {
        List<AvailabilityTemplateIntervalBreak> sourceBreaks = availabilityTemplateIntervalBreakRepository.findByInterval_IdOrderByStartTimeAsc(sourceInterval.getId());

        if (sourceBreaks == null || sourceBreaks.isEmpty()) {
            return;
        }

        List<AvailabilityTemplateIntervalBreak> clonedBreaks = sourceBreaks.stream()
                .filter(Objects::nonNull)
                .map(sourceBreak -> {
                    AvailabilityTemplateIntervalBreak clonedBreak = new AvailabilityTemplateIntervalBreak();

                    clonedBreak.setInterval(targetInterval);
                    clonedBreak.setStartTime(sourceBreak.getStartTime());
                    clonedBreak.setEndTime(sourceBreak.getEndTime());
                    return clonedBreak;
                })
                .toList();

        availabilityTemplateIntervalBreakRepository.saveAll(clonedBreaks);
    }

    private void cloneIntervalAllowedServices(AvailabilityTemplateInterval sourceInterval, AvailabilityTemplateInterval targetInterval, AvailabilityTemplate targetTemplate) {
        List<AvailabilityTemplateAllowedService> sourceServices = availabilityTemplateAllowedServiceRepository.findAllByInterval_Id(sourceInterval.getId());

        if (sourceServices == null || sourceServices.isEmpty()) {
            return;
        }

        List<AvailabilityTemplateAllowedService> clonedServices = sourceServices.stream()
                .filter(Objects::nonNull)
                .map(sourceService -> {
                    AvailabilityTemplateAllowedService clonedService = new AvailabilityTemplateAllowedService();

                    clonedService.setTemplate(targetTemplate);
                    clonedService.setInterval(targetInterval);
                    clonedService.setService(sourceService.getService());

                    return clonedService;
                })
                .toList();

        availabilityTemplateAllowedServiceRepository.saveAllAndFlush(clonedServices);
    }

}