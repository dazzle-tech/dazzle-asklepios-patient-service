package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderResponseVM;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for managing {@link DiagnosticOrder} resources.
 *
 * <p>Endpoints in this controller cover:</p>
 * <ul>
 *   <li>CRUD operations (create/update/get/delete)</li>
 *   <li>List endpoints by patient / encounter / patient+encounter (legacy-style paths)</li>
 *   <li>A generic filter endpoint (query-params based, optional filters, pagination)</li>
 *   <li>Submit draft orders</li>
 * </ul>
 *
 * <p>Filtering endpoints are designed for exact matching (no contains/like search).
 * Pagination headers are generated via {@link PaginationUtil}.</p>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderController.class);

    private final DiagnosticOrderService diagnosticOrderService;
    private final DiagnosticOrderRepository diagnosticOrderRepository;

    /**
     * Creates a new controller instance.
     *
     * @param diagnosticOrderService service layer for diagnostic orders
     * @param diagnosticOrderRepository repository for diagnostic order persistence and queries
     */
    public DiagnosticOrderController(
            DiagnosticOrderService diagnosticOrderService,
            DiagnosticOrderRepository diagnosticOrderRepository
    ) {
        this.diagnosticOrderService = diagnosticOrderService;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
    }

    /**
     * Returns the current authenticated username.
     *
     * @return username/login of the current authenticated user
     * @throws BadRequestAlertException if no authenticated user is available
     */
    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_orders",
                        "No authenticated user"
                ));
    }

    /**
     * Creates a new {@link DiagnosticOrder}.
     *
     * @param dto payload for creating a diagnostic order
     * @return created entity mapped to response VM (HTTP 201)
     * @throws BadRequestAlertException if validation fails in the service layer
     */
    @PostMapping("/diagnostic-orders")
    public ResponseEntity<DiagnosticOrderResponseVM> create(@Valid @RequestBody DiagnosticOrderCreateDTO dto) {
        LOG.debug("[DiagnosticOrder] CREATE - request received. payload={}", dto);

        DiagnosticOrder saved = diagnosticOrderService.create(dto);

        LOG.debug("[DiagnosticOrder] CREATE - created successfully. id={}", saved.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-orders/" + saved.getId()))
                .body(DiagnosticOrderResponseVM.ofEntity(saved));
    }

    /**
     * Updates an existing {@link DiagnosticOrder}.
     *
     * <p>The URL {@code {id}} is the source of truth for the record to update.
     * A new DTO instance is built using the path id to avoid inconsistencies between path and body.</p>
     *
     * @param id  diagnostic order id (path variable)
     * @param dto payload for updating the order
     * @return updated entity mapped to response VM (HTTP 200)
     * @throws BadRequestAlertException if the order does not exist
     */
    @PutMapping("/diagnostic-orders/{id}")
    public ResponseEntity<DiagnosticOrderResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticOrderUpdateDTO dto
    ) {
        LOG.debug("[DiagnosticOrder] UPDATE - request received. id={} payload={}", id, dto);

        DiagnosticOrder existing = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        DiagnosticOrderUpdateDTO fixed = new DiagnosticOrderUpdateDTO(
                id,
                dto.patientId(),
                dto.encounterId(),
                dto.status(),
                dto.saveDraft(),
                dto.submittedBy(),
                dto.submittedDate(),
                dto.isUrgent(),
                dto.fromDepartmentId(),
                dto.fromFacilityId()
        );

        DiagnosticOrder updated = diagnosticOrderService.update(existing, fixed);

        LOG.debug("[DiagnosticOrder] UPDATE - updated successfully. id={}", updated.getId());
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(updated));
    }

    /**
     * Retrieves a {@link DiagnosticOrder} by id.
     *
     * @param id diagnostic order id
     * @return found entity mapped to response VM (HTTP 200)
     * @throws BadRequestAlertException if the order does not exist
     */
    @GetMapping("/diagnostic-orders/{id}")
    public ResponseEntity<DiagnosticOrderResponseVM> getById(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrder] GET_BY_ID - request received. id={}", id);

        DiagnosticOrder existing = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        LOG.debug("[DiagnosticOrder] GET_BY_ID - found. id={}", existing.getId());
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(existing));
    }

    /**
     * Lists diagnostic orders for a given patient (legacy-style endpoint).
     *
     * @param patientId patient id
     * @param status optional status filter (exact match)
     * @param pageable pagination and sorting
     * @return list of orders mapped to response VMs with pagination headers (HTTP 200)
     */
    @GetMapping("/patients/{patientId}/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(name = "status", required = false) String status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT - request received. patientId={} status={} pageable={}",
                patientId, status, pageable);

        Page<DiagnosticOrder> page = diagnosticOrderService.findByPatient(patientId, status, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT - response ready. patientId={} returned={} totalElements={} totalPages={}",
                patientId, body.size(), page.getTotalElements(), page.getTotalPages());

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * Lists diagnostic orders for a given patient and encounter (legacy-style endpoint).
     *
     * @param patientId patient id
     * @param encounterId encounter id
     * @param status optional status filter (exact match)
     * @param pageable pagination and sorting
     * @return list of orders mapped to response VMs with pagination headers (HTTP 200)
     */
    @GetMapping("/patients/{patientId}/encounters/{encounterId}/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByPatientAndEncounter(
            @PathVariable Long patientId,
            @PathVariable Long encounterId,
            @RequestParam(name = "status", required = false) String status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT_AND_ENCOUNTER - request received. patientId={} encounterId={} status={} pageable={}",
                patientId, encounterId, status, pageable);

        Page<DiagnosticOrder> page =
                diagnosticOrderService.findByPatientAndEncounter(patientId, encounterId, status, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT_AND_ENCOUNTER - response ready. patientId={} encounterId={} returned={} totalElements={} totalPages={}",
                patientId, encounterId, body.size(), page.getTotalElements(), page.getTotalPages());

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * Lists diagnostic orders for a given encounter (legacy-style endpoint).
     *
     * @param encounterId encounter id
     * @param status optional status filter (exact match)
     * @param pageable pagination and sorting
     * @return list of orders mapped to response VMs with pagination headers (HTTP 200)
     */
    @GetMapping("/encounters/{encounterId}/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByEncounter(
            @PathVariable Long encounterId,
            @RequestParam(name = "status", required = false) String status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] LIST_BY_ENCOUNTER - request received. encounterId={} status={} pageable={}",
                encounterId, status, pageable);

        Page<DiagnosticOrder> page = diagnosticOrderService.findByEncounter(encounterId, status, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] LIST_BY_ENCOUNTER - response ready. encounterId={} returned={} totalElements={} totalPages={}",
                encounterId, body.size(), page.getTotalElements(), page.getTotalPages());

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * Deletes a {@link DiagnosticOrder} by id.
     *
     * @param id diagnostic order id
     * @return HTTP 204 if deleted
     * @throws BadRequestAlertException if the order does not exist
     */
    @DeleteMapping("/diagnostic-orders/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrder] DELETE - request received. id={}", id);

        DiagnosticOrder existing = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        diagnosticOrderService.delete(existing.getId());

        LOG.debug("[DiagnosticOrder] DELETE - deleted successfully. id={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Filters diagnostic orders (exact matching, not a text search).
     *
     * <p>Returns a paginated list of {@link DiagnosticOrder} records using optional query parameters.
     * All filters are applied with exact semantics ({@code =}, {@code IN}, {@code NOT IN}) and optional date ranges.</p>
     *
     * <p>Common UI patterns:</p>
     * <ul>
     *   <li><b>Default list (exclude COMPLETED)</b>: pass {@code excludeStatus=COMPLETED}.</li>
     *   <li><b>Show cancelled</b>: pass {@code status=COMPLETED}.</li>
     *   <li><b>Multiple statuses</b>: pass {@code statusIn=NEW,SUBMITTED}.</li>
     *   <li><b>Exclude multiple statuses</b>: pass {@code statusNotIn=CANCELLED&statusNotIn=DELETED}.</li>
     * </ul>
     *
     * @param patientId optional patient identifier to scope results to a specific patient
     * @param encounterId optional encounter identifier to scope results to a specific encounter
     * @param status optional exact status filter (mutually exclusive with {@code statusIn})
     * @param statusIn optional list of statuses to include
     * @param statusNotIn optional list of statuses to exclude
     * @param excludeStatus optional single status to exclude (convenience parameter)
     * @param saveDraft optional filter by draft flag
     * @param isUrgent optional filter by urgency flag
     * @param labStatus optional exact lab status filter
     * @param radStatus optional exact radiology status filter
     * @param submittedDateFrom optional lower bound (inclusive) for {@code submittedDate}
     * @param submittedDateTo optional upper bound (inclusive) for {@code submittedDate}
     * @param departmentId optional department scope for orders having a non-cancelled lab test received by this department
     * @param pageable pagination and sorting information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and a list of filtered diagnostic orders in the body,
     * along with pagination headers
     * @throws IllegalArgumentException if the request contains conflicting filters (e.g. both {@code status} and {@code statusIn})
     */
    @GetMapping("/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> filter(
            @RequestParam(name = "patientId", required = false) Long patientId,
            @RequestParam(name = "encounterId", required = false) Long encounterId,

            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "statusIn", required = false) List<String> statusIn,
            @RequestParam(name = "statusNotIn", required = false) List<String> statusNotIn,
            @RequestParam(name = "excludeStatus", required = false) String excludeStatus,

            @RequestParam(name = "saveDraft", required = false) Boolean saveDraft,
            @RequestParam(name = "isUrgent", required = false) Boolean isUrgent,
            @RequestParam(name = "labStatus", required = false) String labStatus,
            @RequestParam(name = "radStatus", required = false) String radStatus,

            @RequestParam(name = "submittedDateFrom", required = false) Instant submittedDateFrom,
            @RequestParam(name = "submittedDateTo", required = false) Instant submittedDateTo,

            @RequestParam(name = "departmentId", required = false) Long departmentId,

            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] FILTER - request received. patientId={} encounterId={} status={} statusIn={} statusNotIn={} excludeStatus={} saveDraft={} isUrgent={} labStatus={} radStatus={} submittedDateFrom={} submittedDateTo={} departmentId={} pageable={}",
                patientId, encounterId, status, statusIn, statusNotIn, excludeStatus, saveDraft, isUrgent, labStatus, radStatus, submittedDateFrom, submittedDateTo, departmentId, pageable);

        if (status != null && statusIn != null && !statusIn.isEmpty()) {
            throw new IllegalArgumentException("Use either status or statusIn, not both");
        }

        Specification<DiagnosticOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Base filters
            if (patientId != null) predicates.add(cb.equal(root.get("patientId"), patientId));
            if (encounterId != null) predicates.add(cb.equal(root.get("encounterId"), encounterId));

            if (status != null && !status.isBlank()) predicates.add(cb.equal(root.get("status"), status));
            if (statusIn != null && !statusIn.isEmpty()) predicates.add(root.get("status").in(statusIn));
            if (excludeStatus != null && !excludeStatus.isBlank())
                predicates.add(cb.notEqual(root.get("status"), excludeStatus));
            if (statusNotIn != null && !statusNotIn.isEmpty())
                predicates.add(cb.not(root.get("status").in(statusNotIn)));

            if (saveDraft != null) predicates.add(cb.equal(root.get("saveDraft"), saveDraft));
            if (isUrgent != null) predicates.add(cb.equal(root.get("isUrgent"), isUrgent));

            if (labStatus != null && !labStatus.isBlank())
                predicates.add(cb.equal(root.get("labStatus"), labStatus));
            if (radStatus != null && !radStatus.isBlank())
                predicates.add(cb.equal(root.get("radStatus"), radStatus));

            if (submittedDateFrom != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("submittedDate"), submittedDateFrom));
            if (submittedDateTo != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("submittedDate"), submittedDateTo));

            if (departmentId != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<DiagnosticOrderTest> t = sq.from(DiagnosticOrderTest.class);

                sq.select(t.get("id"))
                        .where(
                                cb.equal(t.get("orderId"), root.get("id")),
                                cb.equal(t.get("receivedDepartmentId"), departmentId),
                                cb.notEqual(t.get("status"), DiagnosticStatus.CANCELLED),
                                cb.equal(t.get("orderType"), TestType.LABORATORY)
                        );

                predicates.add(cb.exists(sq));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrder> page = diagnosticOrderRepository.findAll(spec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] FILTER - response ready. returned={} totalElements={} totalPages={}",
                body.size(), page.getTotalElements(), page.getTotalPages());

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * Submits an existing diagnostic order.
     *
     * <p>This endpoint converts a draft order into a submitted order. It rejects the request if the order
     * is already submitted (i.e., {@code saveDraft} is {@code false}).</p>
     *
     * @param id diagnostic order id
     * @return submitted order response
     * @throws BadRequestAlertException if the order does not exist or is already submitted
     */
    @PostMapping("/diagnostic-orders/{id}/submit")
    public ResponseEntity<DiagnosticOrderResponseVM> submit(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrder] SUBMIT - request received. id={}", id);

        DiagnosticOrder existing = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        if (Boolean.FALSE.equals(existing.getSaveDraft())) {
            throw new BadRequestAlertException(
                    "already_submitted",
                    "diagnostic_orders",
                    "Order already submitted"
            );
        }

        String username = currentUsername();

        DiagnosticOrder saved = diagnosticOrderService.submit(existing, username);

        LOG.debug("[DiagnosticOrder] SUBMIT - submitted successfully. id={} submittedBy={}", saved.getId(), username);
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(saved));
    }
}
