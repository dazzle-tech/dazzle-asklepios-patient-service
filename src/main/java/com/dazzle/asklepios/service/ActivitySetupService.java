package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ActivityCategory;
import com.dazzle.asklepios.domain.ActivityPlan;
import com.dazzle.asklepios.domain.ActivityPlanItem;
import com.dazzle.asklepios.domain.ActivityTemplate;
import com.dazzle.asklepios.domain.enumeration.ActivityTemplateKind;
import com.dazzle.asklepios.domain.enumeration.JobRole;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.repository.ActivityCategoryRepository;
import com.dazzle.asklepios.repository.ActivityPlanRepository;
import com.dazzle.asklepios.repository.ActivityTemplateRepository;
import com.dazzle.asklepios.service.dto.activitySetup.ActivityCategoryDTO;
import com.dazzle.asklepios.service.dto.activitySetup.ActivityPlanDTO;
import com.dazzle.asklepios.service.dto.activitySetup.ActivityPlanItemDTO;
import com.dazzle.asklepios.service.dto.activitySetup.ActivityTemplateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Setup for the categories and templates that drive patient activities and tasks.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ActivitySetupService {

    private static final Logger LOG = LoggerFactory.getLogger(ActivitySetupService.class);
    private static final String ENTITY = "activitySetup";
    private static final Set<String> JOB_ROLES = Arrays.stream(JobRole.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final ActivityCategoryRepository categoryRepository;
    private final ActivityTemplateRepository templateRepository;
    private final ActivityPlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<ActivityCategory> findAllCategories() {
        return categoryRepository.findAllByOrderByViewOrderAscNameAsc();
    }

    public ActivityCategory createCategory(ActivityCategoryDTO dto) {
        String code = dto.getCode().trim();
        if (categoryRepository.existsByCode(code)) {
            throw bad("A category with this code already exists.", "activityCategory.code.exists");
        }
        ActivityCategory category = new ActivityCategory();
        category.setCode(code);
        applyCategory(category, dto);
        LOG.info("[CREATE] ActivityCategory code={}", code);
        return categoryRepository.save(category);
    }

    public ActivityCategory updateCategory(Long id, ActivityCategoryDTO dto) {
        ActivityCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> bad("Category not found.", "activityCategory.notFound"));
        applyCategory(category, dto);
        LOG.info("[UPDATE] ActivityCategory id={}", id);
        return categoryRepository.save(category);
    }

    public ActivityCategory toggleCategory(Long id) {
        ActivityCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> bad("Category not found.", "activityCategory.notFound"));
        category.setIsActive(!Boolean.TRUE.equals(category.getIsActive()));
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public List<ActivityTemplate> findAllTemplates() {
        return templateRepository.findAllByOrderByViewOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public ActivityTemplate findTemplateByCode(String code) {
        if (code == null || code.isBlank()) {
            throw bad("Activity type is required.", "activityTemplate.code.required");
        }
        return templateRepository.findByCode(code.trim())
                .orElseThrow(() -> bad("Activity type not found.", "activityTemplate.notFound"));
    }

    public ActivityTemplate createTemplate(ActivityTemplateDTO dto) {
        String code = dto.getCode().trim();
        if (templateRepository.existsByCode(code)) {
            throw bad("A template with this code already exists.", "activityTemplate.code.exists");
        }
        ActivityTemplate template = new ActivityTemplate();
        template.setCode(code);
        template.setIsSystem(false);
        applyTemplate(template, dto);
        LOG.info("[CREATE] ActivityTemplate code={}", code);
        return templateRepository.save(template);
    }

    public ActivityTemplate updateTemplate(Long id, ActivityTemplateDTO dto) {
        ActivityTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> bad("Template not found.", "activityTemplate.notFound"));
        applyTemplate(template, dto);
        LOG.info("[UPDATE] ActivityTemplate id={}", id);
        return templateRepository.save(template);
    }

    public ActivityTemplate toggleTemplate(Long id) {
        ActivityTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> bad("Template not found.", "activityTemplate.notFound"));
        template.setIsActive(!Boolean.TRUE.equals(template.getIsActive()));
        return templateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public List<ActivityPlan> findAllPlans() {
        return planRepository.findAllByOrderByViewOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public ActivityPlan findPlanByCode(String code) {
        if (code == null || code.isBlank()) {
            throw bad("Plan is required.", "activityPlan.code.required");
        }
        return planRepository.findByCode(code.trim())
                .orElseThrow(() -> bad("Plan not found.", "activityPlan.notFound"));
    }

    public ActivityPlan createPlan(ActivityPlanDTO dto) {
        String code = dto.getCode().trim();
        if (planRepository.existsByCode(code)) {
            throw bad("A plan with this code already exists.", "activityPlan.code.exists");
        }
        ActivityPlan plan = new ActivityPlan();
        plan.setCode(code);
        applyPlan(plan, dto);
        LOG.info("[CREATE] ActivityPlan code={}", code);
        return planRepository.save(plan);
    }

    public ActivityPlan updatePlan(Long id, ActivityPlanDTO dto) {
        ActivityPlan plan = planRepository.findById(id)
                .orElseThrow(() -> bad("Plan not found.", "activityPlan.notFound"));
        applyPlan(plan, dto);
        LOG.info("[UPDATE] ActivityPlan id={}", id);
        return planRepository.save(plan);
    }

    public ActivityPlan togglePlan(Long id) {
        ActivityPlan plan = planRepository.findById(id)
                .orElseThrow(() -> bad("Plan not found.", "activityPlan.notFound"));
        plan.setIsActive(!Boolean.TRUE.equals(plan.getIsActive()));
        return planRepository.save(plan);
    }

    /**
     * Due time from the template rule, counted from {@code from}; null when the template has no rule.
     */
    public Instant resolveDueAt(ActivityTemplate template, Instant from) {
        if (template.getDueOffset() == null || template.getDueUnit() == null) {
            return null;
        }
        Duration offset = switch (template.getDueUnit()) {
            case HOURS -> Duration.ofHours(template.getDueOffset());
            case DAYS -> Duration.ofDays(template.getDueOffset());
        };
        return from.plus(offset);
    }

    /** Keeps known job roles only, without duplicates, in the given order. */
    public List<String> cleanRoles(List<String> roles) {
        if (roles == null) {
            return new ArrayList<>();
        }
        List<String> unknown = roles.stream()
                .filter(Objects::nonNull)
                .filter(role -> !JOB_ROLES.contains(role))
                .toList();
        if (!unknown.isEmpty()) {
            throw bad("Unknown job role: " + String.join(", ", unknown), "activitySetup.jobRole.invalid");
        }
        return new ArrayList<>(new LinkedHashSet<>(roles.stream().filter(Objects::nonNull).toList()));
    }

    private void applyCategory(ActivityCategory category, ActivityCategoryDTO dto) {
        category.setName(dto.getName().trim());
        category.setIcon(trimToNull(dto.getIcon()));
        category.setColor(trimToNull(dto.getColor()));
        category.setViewOrder(dto.getViewOrder() == null ? 0 : dto.getViewOrder());
        if (dto.getIsActive() != null) {
            category.setIsActive(dto.getIsActive());
        }
    }

    private void applyTemplate(ActivityTemplate template, ActivityTemplateDTO dto) {
        if (!categoryRepository.existsByCode(dto.getCategoryCode())) {
            throw bad("Category not found.", "activityCategory.notFound");
        }
        if ((dto.getDueOffset() == null) != (dto.getDueUnit() == null)) {
            throw bad("Set both the due offset and its unit, or neither.", "activityTemplate.dueRule.incomplete");
        }
        List<String> outcomes = cleanText(dto.getOutcomeOptions());
        boolean requireOutcome = Boolean.TRUE.equals(dto.getRequireOutcome());
        if (requireOutcome && outcomes.isEmpty()) {
            throw bad("Add outcome options before making the outcome required.", "activityTemplate.outcome.missing");
        }

        boolean isTask = dto.getKind() == ActivityTemplateKind.TASK;
        template.setName(dto.getName().trim());
        template.setDescription(trimToNull(dto.getDescription()));
        template.setCategoryCode(dto.getCategoryCode());
        template.setKind(dto.getKind());
        template.setDefaultPriority(dto.getDefaultPriority() == null ? PatientActivityPriority.NORMAL : dto.getDefaultPriority());
        template.setDueOffset(isTask ? dto.getDueOffset() : null);
        template.setDueUnit(isTask ? dto.getDueUnit() : null);
        template.setAssigneeRoles(cleanRoles(dto.getAssigneeRoles()));
        template.setCreatorRoles(cleanRoles(dto.getCreatorRoles()));
        template.setOutcomeOptions(outcomes);
        template.setChecklist(isTask ? cleanText(dto.getChecklist()) : new ArrayList<>());
        template.setRequireOutcome(requireOutcome);
        template.setViewOrder(dto.getViewOrder() == null ? 0 : dto.getViewOrder());
        if (dto.getIsActive() != null) {
            template.setIsActive(dto.getIsActive());
        }
    }

    private void applyPlan(ActivityPlan plan, ActivityPlanDTO dto) {
        List<ActivityPlanItem> items = new ArrayList<>();
        for (ActivityPlanItemDTO itemDto : dto.getItems()) {
            ActivityTemplate template = findTemplateByCode(itemDto.getTemplateCode());
            if (template.getKind() != ActivityTemplateKind.TASK) {
                throw bad("Only tasks can go in a plan: " + template.getName(), "activityPlan.item.notTask");
            }
            if ((itemDto.getDueOffset() == null) != (itemDto.getDueUnit() == null)) {
                throw bad("Set both the due offset and its unit, or neither: " + template.getName(),
                        "activityPlan.item.dueRule.incomplete");
            }
            ActivityPlanItem item = new ActivityPlanItem();
            item.setTemplateCode(template.getCode());
            item.setDueOffset(itemDto.getDueOffset());
            item.setDueUnit(itemDto.getDueUnit());
            item.setNote(trimToNull(itemDto.getNote()));
            items.add(item);
        }

        plan.setName(dto.getName().trim());
        plan.setDescription(trimToNull(dto.getDescription()));
        plan.setItems(items);
        plan.setViewOrder(dto.getViewOrder() == null ? 0 : dto.getViewOrder());
        if (dto.getIsActive() != null) {
            plan.setIsActive(dto.getIsActive());
        }
    }

    private List<String> cleanText(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList()));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BadRequestAlertException bad(String message, String errorKey) {
        return new BadRequestAlertException(message, ENTITY, errorKey);
    }
}
