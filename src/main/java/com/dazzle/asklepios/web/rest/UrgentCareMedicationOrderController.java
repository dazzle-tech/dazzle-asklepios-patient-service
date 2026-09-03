package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.UrgentCareMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.AdministerMedicationOrderDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderUpdateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.commands.UrgentCareMedicationOrderCancelDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.commands.UrgentCareMedicationOrderDiscardDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.commands.UrgentCareMedicationOrderSubmitDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.criteria.Predicate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
public class UrgentCareMedicationOrderController {

    private static final Logger LOG = LoggerFactory.getLogger(UrgentCareMedicationOrderController.class);

    private final com.dazzle.asklepios.service.UrgentCareMedicationOrderService UrgentCareMedicationOrderService;

    public UrgentCareMedicationOrderController(
            com.dazzle.asklepios.service.UrgentCareMedicationOrderService UrgentCareMedicationOrderService
    ) {
        this.UrgentCareMedicationOrderService = UrgentCareMedicationOrderService;
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

    @PostMapping("/urgent-care-medication-orders")
    public ResponseEntity<UrgentCareMedicationOrder> create(@Valid @RequestBody UrgentCareMedicationOrderCreateDTO dto) {
        LOG.debug("[CREATE] request -> {}", dto);

        UrgentCareMedicationOrder result = UrgentCareMedicationOrderService.create(dto);

        LOG.debug("[CREATE] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/urgent-care-medication-orders/{id}")
    public ResponseEntity<UrgentCareMedicationOrder> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UrgentCareMedicationOrderUpdateDTO dto
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

        UrgentCareMedicationOrder existing = UrgentCareMedicationOrderService.findOne(id);
        UrgentCareMedicationOrder updated = UrgentCareMedicationOrderService.update(existing, dto);

        LOG.debug("[UPDATE] response -> id={} status={}", updated.getId(), updated.getStatus());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/urgent-care-medication-orders/{id}")
    public ResponseEntity<UrgentCareMedicationOrder> getById(@PathVariable("id") Long id) {
        LOG.debug("[GET_BY_ID] request -> id={}", id);

        UrgentCareMedicationOrder result = UrgentCareMedicationOrderService.findOne(id);

        LOG.debug("[GET_BY_ID] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/urgent-care-medication-orders")
    public ResponseEntity<Page<UrgentCareMedicationOrder>> getAll(
            @RequestParam(name = "status", required = false) MedicationOrderStatus status,
            Pageable pageable
    ) {
        LOG.debug("[GET_ALL] request -> status={} pageable={}", status, pageable);

        Page<UrgentCareMedicationOrder> page;
        if (status != null) {
            page = UrgentCareMedicationOrderService.findByStatus(status, pageable);
        } else {
            page = UrgentCareMedicationOrderService.findAll(pageable);
        }

        LOG.debug("[GET_ALL] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return ResponseEntity.ok(page);
    }

    @GetMapping("/urgent-care-medication-orders/filter")
    public ResponseEntity<Page<UrgentCareMedicationOrder>> filter(
            @RequestParam(name = "patientId", required = false) Long patientId,
            @RequestParam(name = "encounterId", required = false) Long encounterId,
            @RequestParam(name = "activeIngredientId", required = false) Long activeIngredientId,
            @RequestParam(name = "status", required = false) MedicationOrderStatus status,
            @RequestParam(name = "statusIn", required = false) List<MedicationOrderStatus> statusIn,
            @RequestParam(name = "statusNotIn", required = false) List<MedicationOrderStatus> statusNotIn,
            @RequestParam(name = "route", required = false) String route,
            @RequestParam(name = "frequency", required = false) String frequency,
            @RequestParam(required = false) LocalDate orderDateFrom,
            @RequestParam(required = false) LocalDate orderDateTo,
            @RequestParam(required = false) List<Long> patientIds,

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

        Specification<UrgentCareMedicationOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (patientId != null) {
                LOG.debug("[FILTER] apply patientId={}", patientId);
                predicates.add(cb.equal(root.get("patient").get("id"), patientId));
            }

            if (patientIds != null && !patientIds.isEmpty()) {
                predicates.add(root.get("patient").get("id").in(patientIds));
            }


            if (orderDateFrom != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdDate"),
                                orderDateFrom.atStartOfDay(ZoneOffset.UTC).toInstant()
                        )
                );
            }

            if (orderDateTo != null) {
                predicates.add(
                        cb.lessThan(
                                root.get("createdDate"),
                                orderDateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                        )
                );
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

        Page<UrgentCareMedicationOrder> page = UrgentCareMedicationOrderService.filter(spec, pageable);

        LOG.debug("[FILTER] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return ResponseEntity.ok(page);
    }

    @PostMapping("/urgent-care-medication-orders/{id}/submit")
    public ResponseEntity<UrgentCareMedicationOrder> submit(
            @PathVariable("id") Long id,
            @Valid @RequestBody UrgentCareMedicationOrderSubmitDTO dto
    ) {
        LOG.debug("[SUBMIT] request -> id={} isHighAlert={}", id, dto.isHighAlert());

        UrgentCareMedicationOrder result = UrgentCareMedicationOrderService.submit(
                id,
                currentUsername(),
                dto.isHighAlert()
        );

        LOG.debug("[SUBMIT] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/urgent-care-medication-orders/{id}/administer")
    public ResponseEntity<UrgentCareMedicationOrder> administer(@PathVariable("id") Long id , @RequestBody AdministerMedicationOrderDTO dto) {
        LOG.debug("[ADMINISTER] request -> id={}", id);

        UrgentCareMedicationOrder result =
                UrgentCareMedicationOrderService.administer(id, currentUsername(),dto.actualAdministerTime());

        LOG.debug("[ADMINISTER] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/urgent-care-medication-orders/{id}/double-check")
    public ResponseEntity<UrgentCareMedicationOrder> doubleCheck(@PathVariable("id") Long id) {
        LOG.debug("[DOUBLE_CHECK] request -> id={}", id);

        UrgentCareMedicationOrder result =
                UrgentCareMedicationOrderService.doubleCheck(id, currentUsername());

        LOG.debug("[DOUBLE_CHECK] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/urgent-care-medication-orders/{id}/discard")
    public ResponseEntity<UrgentCareMedicationOrder> discard(
            @PathVariable("id") Long id,
            @Valid @RequestBody UrgentCareMedicationOrderDiscardDTO dto
    ) {
        LOG.debug("[DISCARD] request -> id={} reason={}", id, dto.discardReason());

        UrgentCareMedicationOrder result = UrgentCareMedicationOrderService.discard(
                id,
                currentUsername(),
                dto.discardReason()
        );

        LOG.debug("[DISCARD] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/urgent-care-medication-orders/{id}/cancel")
    public ResponseEntity<UrgentCareMedicationOrder> cancel(
            @PathVariable("id") Long id,
            @Valid @RequestBody UrgentCareMedicationOrderCancelDTO dto
    ) {
        LOG.debug("[CANCEL] request -> id={} reason={}", id, dto.cancellationReason());

        UrgentCareMedicationOrder result = UrgentCareMedicationOrderService.cancel(
                id,
                currentUsername(),
                dto.cancellationReason()
        );

        LOG.debug("[CANCEL] response -> id={} status={}", result.getId(), result.getStatus());
        return ResponseEntity.ok(result);
    }
    @PutMapping("/urgent-care-medication-orders/{orderId}/actual-administer-time")
    public ResponseEntity<UrgentCareMedicationOrder> setActualAdministerTime(
            @PathVariable Long orderId,
            @RequestBody Instant actualAdministerTime
            ) {

        LOG.debug(
                "REST request to set actual administer time. orderId={} actualAdministerTime={}",
                orderId,
                actualAdministerTime
        );

        UrgentCareMedicationOrder result =
                UrgentCareMedicationOrderService.setActualAdministerTime(
                        orderId,
                        actualAdministerTime
                );

        return ResponseEntity.ok(result);
    }
}