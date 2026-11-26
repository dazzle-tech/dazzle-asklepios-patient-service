package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ActivityCategory;
import com.dazzle.asklepios.domain.ActivityPlan;
import com.dazzle.asklepios.domain.ActivityTemplate;
import com.dazzle.asklepios.service.ActivitySetupService;
import com.dazzle.asklepios.service.dto.activitySetup.ActivityCategoryDTO;
import com.dazzle.asklepios.service.dto.activitySetup.ActivityPlanDTO;
import com.dazzle.asklepios.service.dto.activitySetup.ActivityTemplateDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class ActivitySetupController {

    private static final Logger LOG = LoggerFactory.getLogger(ActivitySetupController.class);

    private final ActivitySetupService activitySetupService;

    public ActivitySetupController(ActivitySetupService activitySetupService) {
        this.activitySetupService = activitySetupService;
    }

    @GetMapping("/activity-categories")
    public ResponseEntity<List<ActivityCategory>> findAllCategories() {
        LOG.debug("REST list ActivityCategory");
        return ResponseEntity.ok(activitySetupService.findAllCategories());
    }

    @PostMapping("/activity-categories")
    public ResponseEntity<ActivityCategory> createCategory(@Valid @RequestBody ActivityCategoryDTO dto) {
        LOG.debug("REST create ActivityCategory code={}", dto.getCode());
        return ResponseEntity.ok(activitySetupService.createCategory(dto));
    }

    @PutMapping("/activity-categories/{id}")
    public ResponseEntity<ActivityCategory> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody ActivityCategoryDTO dto
    ) {
        LOG.debug("REST update ActivityCategory id={}", id);
        return ResponseEntity.ok(activitySetupService.updateCategory(id, dto));
    }

    @PutMapping("/activity-categories/{id}/toggle-active")
    public ResponseEntity<ActivityCategory> toggleCategory(@PathVariable Long id) {
        LOG.debug("REST toggle ActivityCategory id={}", id);
        return ResponseEntity.ok(activitySetupService.toggleCategory(id));
    }

    @GetMapping("/activity-templates")
    public ResponseEntity<List<ActivityTemplate>> findAllTemplates() {
        LOG.debug("REST list ActivityTemplate");
        return ResponseEntity.ok(activitySetupService.findAllTemplates());
    }

    @PostMapping("/activity-templates")
    public ResponseEntity<ActivityTemplate> createTemplate(@Valid @RequestBody ActivityTemplateDTO dto) {
        LOG.debug("REST create ActivityTemplate code={}", dto.getCode());
        return ResponseEntity.ok(activitySetupService.createTemplate(dto));
    }

    @PutMapping("/activity-templates/{id}")
    public ResponseEntity<ActivityTemplate> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody ActivityTemplateDTO dto
    ) {
        LOG.debug("REST update ActivityTemplate id={}", id);
        return ResponseEntity.ok(activitySetupService.updateTemplate(id, dto));
    }

    @PutMapping("/activity-templates/{id}/toggle-active")
    public ResponseEntity<ActivityTemplate> toggleTemplate(@PathVariable Long id) {
        LOG.debug("REST toggle ActivityTemplate id={}", id);
        return ResponseEntity.ok(activitySetupService.toggleTemplate(id));
    }

    @GetMapping("/activity-plans")
    public ResponseEntity<List<ActivityPlan>> findAllPlans() {
        LOG.debug("REST list ActivityPlan");
        return ResponseEntity.ok(activitySetupService.findAllPlans());
    }

    @PostMapping("/activity-plans")
    public ResponseEntity<ActivityPlan> createPlan(@Valid @RequestBody ActivityPlanDTO dto) {
        LOG.debug("REST create ActivityPlan code={}", dto.getCode());
        return ResponseEntity.ok(activitySetupService.createPlan(dto));
    }

    @PutMapping("/activity-plans/{id}")
    public ResponseEntity<ActivityPlan> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody ActivityPlanDTO dto
    ) {
        LOG.debug("REST update ActivityPlan id={}", id);
        return ResponseEntity.ok(activitySetupService.updatePlan(id, dto));
    }

    @PutMapping("/activity-plans/{id}/toggle-active")
    public ResponseEntity<ActivityPlan> togglePlan(@PathVariable Long id) {
        LOG.debug("REST toggle ActivityPlan id={}", id);
        return ResponseEntity.ok(activitySetupService.togglePlan(id));
    }
}
