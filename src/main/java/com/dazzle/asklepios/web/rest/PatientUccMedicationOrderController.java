package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PatientUccMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.PatientUccMedicationOrderService;
import com.dazzle.asklepios.service.PatientUccMedicationOrderStatusService;
import com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.PatientUccMedicationOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.PatientUccMedicationOrderUpdateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.commands.PatientUccMedicationOrderCancelDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.commands.PatientUccMedicationOrderDiscardDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.commands.PatientUccMedicationOrderSubmitDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientUccMedicationOrderController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientUccMedicationOrderController.class);

    private final PatientUccMedicationOrderService patientUccMedicationOrderService;
    private final PatientUccMedicationOrderStatusService patientUccMedicationOrderStatusService;

    public PatientUccMedicationOrderController(
            PatientUccMedicationOrderService patientUccMedicationOrderService,
            PatientUccMedicationOrderStatusService patientUccMedicationOrderStatusService
    ) {
        this.patientUccMedicationOrderService = patientUccMedicationOrderService;
        this.patientUccMedicationOrderStatusService = patientUccMedicationOrderStatusService;
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> {
                    LOG.error("[AUTH] No authenticated user found");
                    return new BadRequestAlertException(
                            "unauthenticated",
                            "patient_ucc_medication_order",
                            "No authenticated user"
                    );
                });

        LOG.debug("[AUTH] currentUsername={}", username);
        return username;
    }

    @PostMapping("/ucc-medication-orders")
    public ResponseEntity<PatientUccMedicationOrder> create(@Valid @RequestBody PatientUccMedicationOrderCreateDTO dto) {
        LOG.debug("[CREATE] request -> {}", dto);

        PatientUccMedicationOrder result = patientUccMedicationOrderService.create(dto);

        LOG.debug("[CREATE] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/ucc-medication-orders/{id}")
    public ResponseEntity<PatientUccMedicationOrder> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatientUccMedicationOrderUpdateDTO dto
    ) {
        LOG.debug("[UPDATE] request -> pathId={} payload={}", id, dto);

        if (!id.equals(dto.id())) {
            LOG.error("[UPDATE] id mismatch -> pathId={} dtoId={}", id, dto.id());
            throw new BadRequestAlertException(
                    "idmismatch",
                    "patient_ucc_medication_order",
                    "Path id and body id mismatch"
            );
        }

        PatientUccMedicationOrder existing = patientUccMedicationOrderService.findOne(id);
        PatientUccMedicationOrder updated = patientUccMedicationOrderService.update(existing, dto);

        LOG.debug("[UPDATE] response -> id={} status={}", updated.getId(), updated.getStatus());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/ucc-medication-orders/{id}")
    public ResponseEntity<PatientUccMedicationOrder> getById(@PathVariable("id") Long id) {
        LOG.debug("[GET_BY_ID] request -> id={}", id);

        PatientUccMedicationOrder result = patientUccMedicationOrderService.findOne(id);

        LOG.debug("[GET_BY_ID] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ucc-medication-orders")
    public ResponseEntity<Page<PatientUccMedicationOrder>> getAll(
            @RequestParam(name = "status", required = false) MedicationOrderStatus status,
            Pageable pageable
    ) {
        LOG.debug("[GET_ALL] request -> status={} pageable={}", status, pageable);

        Page<PatientUccMedicationOrder> page;
        if (status != null) {
            page = patientUccMedicationOrderService.findByStatus(status, pageable);
        } else {
            page = patientUccMedicationOrderService.findAll(pageable);
        }

        LOG.debug("[GET_ALL] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return ResponseEntity.ok(page);
    }

    @GetMapping("/ucc-medication-orders/filter")
    public ResponseEntity<Page<PatientUccMedicationOrder>> filter(
            @RequestParam(name = "patientId", required = false) Long patientId,
            @RequestParam(name = "encounterId", required = false) Long encounterId,
            @RequestParam(name = "activeIngredientId", required = false) Long activeIngredientId,
            @RequestParam(name = "status", required = false) MedicationOrderStatus status,
            @RequestParam(name = "statusIn", required = false) List<MedicationOrderStatus> statusIn,
            @RequestParam(name = "statusNotIn", required = false) List<MedicationOrderStatus> statusNotIn,
            @RequestParam(name = "route", required = false) String route,
            @RequestParam(name = "frequency", required = false) String frequency,
            Pageable pageable
    ) {
        LOG.debug(
                "[FILTER] params -> patientId={} encounterId={} activeIngredientId={} status={} statusIn={} statusNotIn={} route={} frequency={} pageable={}",
                patientId, encounterId, activeIngredientId, status, statusIn, statusNotIn, route, frequency, pageable
        );

        if (status != null && statusIn != null && !statusIn.isEmpty()) {
            LOG.error("[FILTER] invalid filter combination -> status={} statusIn={}", status, statusIn);
            throw new BadRequestAlertException(
                    "invalid_filter",
                    "patient_ucc_medication_order",
                    "Use either status or statusIn, not both"
            );
        }

        Specification<PatientUccMedicationOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (patientId != null) {
                LOG.debug("[FILTER] apply patientId={}", patientId);
                predicates.add(cb.equal(root.get("patient").get("id"), patientId));
            }

            if (encounterId != null) {
                LOG.debug("[FILTER] apply encounterId={}", encounterId);
                predicates.add(cb.equal(root.get("encounter").get("id"), encounterId));
            }

            if (activeIngredientId != null) {
                LOG.debug("[FILTER] apply activeIngredientId={}", activeIngredientId);
                predicates.add(cb.equal(root.get("activeIngredientId"), activeIngredientId));
            }

            if (status != null) {
                LOG.debug("[FILTER] apply status={}", status);
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (statusIn != null && !statusIn.isEmpty()) {
                LOG.debug("[FILTER] apply status IN {}", statusIn);
                predicates.add(root.get("status").in(statusIn));
            }

            if (statusNotIn != null && !statusNotIn.isEmpty()) {
                LOG.debug("[FILTER] apply status NOT IN {}", statusNotIn);
                predicates.add(cb.not(root.get("status").in(statusNotIn)));
            }

            if (route != null && !route.isBlank()) {
                LOG.debug("[FILTER] apply route={}", route);
                predicates.add(cb.equal(root.get("route"), route));
            }

            if (frequency != null && !frequency.isBlank()) {
                LOG.debug("[FILTER] apply frequency={}", frequency);
                predicates.add(cb.equal(root.get("frequency"), frequency));
            }

            LOG.debug("[FILTER] total predicates={}", predicates.size());
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PatientUccMedicationOrder> page = patientUccMedicationOrderService.filter(spec, pageable);

        LOG.debug("[FILTER] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return ResponseEntity.ok(page);
    }

    @PostMapping("/ucc-medication-orders/{id}/submit")
    public ResponseEntity<PatientUccMedicationOrder> submit(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatientUccMedicationOrderSubmitDTO dto
    ) {
        LOG.debug("[SUBMIT] request -> id={} isHighAlert={}", id, dto.isHighAlert());

        PatientUccMedicationOrder result = patientUccMedicationOrderStatusService.submit(
                id,
                currentUsername(),
                dto.isHighAlert()
        );

        LOG.debug("[SUBMIT] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ucc-medication-orders/{id}/administer")
    public ResponseEntity<PatientUccMedicationOrder> administer(@PathVariable("id") Long id) {
        LOG.debug("[ADMINISTER] request -> id={}", id);

        PatientUccMedicationOrder result =
                patientUccMedicationOrderStatusService.administer(id, currentUsername());

        LOG.debug("[ADMINISTER] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ucc-medication-orders/{id}/double-check")
    public ResponseEntity<PatientUccMedicationOrder> doubleCheck(@PathVariable("id") Long id) {
        LOG.debug("[DOUBLE_CHECK] request -> id={}", id);

        PatientUccMedicationOrder result =
                patientUccMedicationOrderStatusService.doubleCheck(id, currentUsername());

        LOG.debug("[DOUBLE_CHECK] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ucc-medication-orders/{id}/discard")
    public ResponseEntity<PatientUccMedicationOrder> discard(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatientUccMedicationOrderDiscardDTO dto
    ) {
        LOG.debug("[DISCARD] request -> id={} reason={}", id, dto.discardReason());

        PatientUccMedicationOrder result = patientUccMedicationOrderStatusService.discard(
                id,
                currentUsername(),
                dto.discardReason()
        );

        LOG.debug("[DISCARD] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ucc-medication-orders/{id}/cancel")
    public ResponseEntity<PatientUccMedicationOrder> cancel(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatientUccMedicationOrderCancelDTO dto
    ) {
        LOG.debug("[CANCEL] request -> id={} reason={}", id, dto.cancellationReason());

        PatientUccMedicationOrder result = patientUccMedicationOrderStatusService.cancel(
                id,
                currentUsername(),
                dto.cancellationReason()
        );

        LOG.debug("[CANCEL] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }
}