package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
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
 * REST controller for managing Diagnostic Orders.
 * <p>
 * Endpoints in this controller cover:
 * - CRUD operations (create/update/get/delete)
 * - List endpoints by patient / encounter / patient+encounter (legacy-style paths)
 * - A generic filter endpoint (query-params based, optional filters, pagination)
 * <p>
 * Notes:
 * - Filtering endpoints are designed for exact matching (no contains/like search).
 * - Pagination headers are generated via PaginationUtil to match existing style.
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderController.class);

    private final DiagnosticOrderService diagnosticOrderService;
    private final DiagnosticOrderRepository diagnosticOrderRepository;

    public DiagnosticOrderController(DiagnosticOrderService diagnosticOrderService,
                                     DiagnosticOrderRepository diagnosticOrderRepository) {
        this.diagnosticOrderService = diagnosticOrderService;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
    }

    private String currentUsername() {
        LOG.debug("[DiagnosticOrder] CURRENT_USER - resolving username");
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_orders",
                        "No authenticated user"
                ));
    }

    /**
     * Create a new DiagnosticOrder.
     *
     * @param dto payload for creating diagnostic order
     * @return created entity mapped to response VM
     */
    @PostMapping("/diagnostic-orders")
    public ResponseEntity<DiagnosticOrderResponseVM> create(@Valid @RequestBody DiagnosticOrderCreateDTO dto) {
        LOG.debug("[DiagnosticOrder] CREATE - request received. payload={}", dto);

        DiagnosticOrder createdOrder = diagnosticOrderService.create(dto);

        LOG.debug("[DiagnosticOrder] CREATE - created successfully. id={}", createdOrder.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-orders/" + createdOrder.getId()))
                .body(DiagnosticOrderResponseVM.ofEntity(createdOrder));
    }

    /**
     * Update an existing DiagnosticOrder.
     * <p>
     * The URL {id} is the source of truth for the record to update.
     * We rebuild a "fixed" DTO that includes the path id to avoid inconsistencies.
     *
     * @param id  path id
     * @param dto payload for update
     * @return updated entity mapped to response VM
     */
    @PutMapping("/diagnostic-orders/{id}")
    public ResponseEntity<DiagnosticOrderResponseVM> update(@PathVariable Long id,
                                                            @Valid @RequestBody DiagnosticOrderUpdateDTO dto) {
        LOG.debug("[DiagnosticOrder] UPDATE - request received. id={} payload={}", id, dto);

        DiagnosticOrder orderToUpdate = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        // Ensure the DTO id matches the path variable id
        DiagnosticOrderUpdateDTO fixedDto = new DiagnosticOrderUpdateDTO(
                id,
                dto.patientId(),
                dto.encounterId(),
                dto.saveDraft(),
                dto.isUrgent(),
                dto.fromDepartmentId(),
                dto.fromFacilityId()

        );

        DiagnosticOrder updatedOrder = diagnosticOrderService.update(orderToUpdate, fixedDto);

        LOG.debug("[DiagnosticOrder] UPDATE - updated successfully. id={}", updatedOrder.getId());
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(updatedOrder));
    }

    /**
     * Get a DiagnosticOrder by its id.
     */
    @GetMapping("/diagnostic-orders/{id}")
    public ResponseEntity<DiagnosticOrderResponseVM> getById(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrder] GET_BY_ID - request received. id={}", id);

        DiagnosticOrder order = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        LOG.debug("[DiagnosticOrder] GET_BY_ID - found. id={}", order.getId());
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(order));
    }

    /**
     * List diagnostic orders for a given patient (legacy-style endpoint).
     * Supports pagination and optional status filter.
     */
    @GetMapping(" /diagnostic-orders/by-patient/{patientId}")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(name = "status", required = false) DiagnosticStatus status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT - request received. patientId={} status={} pageable={}",
                patientId, status, pageable);

        Page<DiagnosticOrder> ordersPage = (status == null)
                ? diagnosticOrderService.findByPatient(patientId, pageable)
                : diagnosticOrderService.findByPatientAndStatus(patientId, status, pageable);

        HttpHeaders paginationHeaders = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), ordersPage
        );

        List<DiagnosticOrderResponseVM> responseBody = ordersPage.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT - response ready. patientId={} returned={} totalElements={} totalPages={}",
                patientId, responseBody.size(), ordersPage.getTotalElements(), ordersPage.getTotalPages());

        return new ResponseEntity<>(responseBody, paginationHeaders, HttpStatus.OK);
    }

    /**
     * List diagnostic orders for a given patient + encounter (legacy-style endpoint).
     * Supports pagination and optional status filter.
     */
    @GetMapping("/diagnostic-orders/by-patient/{patientId}/by-encounter/{encounterId}")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByPatientAndEncounter(
            @PathVariable Long patientId,
            @PathVariable Long encounterId,
            @RequestParam(name = "status", required = false) DiagnosticStatus status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT_AND_ENCOUNTER - request received. patientId={} encounterId={} status={} pageable={}",
                patientId, encounterId, status, pageable);

        Page<DiagnosticOrder> ordersPage = (status == null)
                ? diagnosticOrderService.findByPatientAndEncounter(patientId, encounterId, pageable)
                : diagnosticOrderService.findByPatientAndEncounterAndStatus(patientId, encounterId, status, pageable);

        HttpHeaders paginationHeaders = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), ordersPage
        );

        List<DiagnosticOrderResponseVM> responseBody = ordersPage.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] LIST_BY_PATIENT_AND_ENCOUNTER - response ready. patientId={} encounterId={} returned={} totalElements={} totalPages={}",
                patientId, encounterId, responseBody.size(), ordersPage.getTotalElements(), ordersPage.getTotalPages());

        return new ResponseEntity<>(responseBody, paginationHeaders, HttpStatus.OK);
    }

    /**
     * List diagnostic orders for a given encounter (legacy-style endpoint).
     * Supports pagination and optional status filter.
     */
    @GetMapping("/diagnostic-orders/by-encounter/{encounterId}")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> getByEncounter(
            @PathVariable Long encounterId,
            @RequestParam(name = "status", required = false) DiagnosticStatus status,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] LIST_BY_ENCOUNTER - request received. encounterId={} status={} pageable={}",
                encounterId, status, pageable);

        Page<DiagnosticOrder> ordersPage = (status == null)
                ? diagnosticOrderService.findByEncounter(encounterId, pageable)
                : diagnosticOrderService.findByEncounterAndStatus(encounterId, status, pageable);

        HttpHeaders paginationHeaders = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), ordersPage
        );

        List<DiagnosticOrderResponseVM> responseBody = ordersPage.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] LIST_BY_ENCOUNTER - response ready. encounterId={} returned={} totalElements={} totalPages={}",
                encounterId, responseBody.size(), ordersPage.getTotalElements(), ordersPage.getTotalPages());

        return new ResponseEntity<>(responseBody, paginationHeaders, HttpStatus.OK);
    }

    /**
     * Delete a DiagnosticOrder by id.
     */
    @DeleteMapping("/diagnostic-orders/{id}")
    public ResponseEntity<Void> delete(@Valid @PathVariable Long id) {
        LOG.debug("[DiagnosticOrder] DELETE - request received. id={}", id);

        DiagnosticOrder orderToDelete = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        diagnosticOrderService.delete(orderToDelete.getId());

        LOG.debug("[DiagnosticOrder] DELETE - deleted successfully. id={}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET /diagnostic-orders} : Filter diagnostic orders (exact matching, not a text search).
     *
     * <p>
     * Returns a paginated list of {@link DiagnosticOrder} records using optional query parameters.
     * All filters are applied with exact semantics ({@code =}, {@code IN}, {@code NOT IN}) and optional date ranges.
     * </p>
     *
     * <p>
     * Common UI patterns:
     * <ul>
     *   <li><b>Default list (exclude COMPLETED)</b>: pass {@code excludeStatus=COMPLETED}.</li>
     *   <li><b>Show cancelled</b>: pass {@code status=COMPLETED}.</li>
     *   <li><b>Multiple statuses</b>: pass {@code statusIn=NEW,SUBMITTED}.</li>
     *   <li><b>Exclude multiple statuses</b>: pass {@code statusNotIn=CANCELLED&statusNotIn=DELETED}.</li>
     * </ul>
     * </p>
     *
     * @param patientId         optional patient identifier to scope results to a specific patient.
     * @param encounterId       optional encounter identifier to scope results to a specific encounter.
     * @param status            optional exact status filter (mutually exclusive with {@code statusIn}).
     * @param statusIn          optional list of statuses to include.
     * @param statusNotIn       optional list of statuses to exclude.
     * @param excludeStatus     optional single status to exclude (convenience parameter).
     * @param saveDraft         optional filter by draft flag.
     * @param isUrgent          optional filter by urgency flag.
     * @param labStatus         optional exact lab status filter.
     * @param radStatus         optional exact radiology status filter.
     * @param submittedDateFrom optional lower bound (inclusive) for {@code submittedDate}.
     * @param submittedDateTo   optional upper bound (inclusive) for {@code submittedDate}.
     * @param pageable          pagination and sorting information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and a list of filtered diagnostic orders in the body,
     * along with pagination headers;
     * or {@code 400 (Bad Request)} if the request contains conflicting filters (e.g. both {@code status} and {@code statusIn}).
     */



    @GetMapping("/diagnostic-orders")
    public ResponseEntity<List<DiagnosticOrderResponseVM>> filter(
            @RequestParam(name = "patientId", required = false) Long patientId,
            @RequestParam(name = "encounterId", required = false) Long encounterId,

            @RequestParam(name = "status", required = false) DiagnosticStatus status,
            @RequestParam(name = "statusIn", required = false) List<DiagnosticStatus> statusIn,
            @RequestParam(name = "statusNotIn", required = false) List<DiagnosticStatus> statusNotIn,
            @RequestParam(name = "excludeStatus", required = false) DiagnosticStatus excludeStatus,

            @RequestParam(name = "saveDraft", required = false) Boolean saveDraft,
            @RequestParam(name = "isUrgent", required = false) Boolean isUrgent,
            @RequestParam(name = "labStatus", required = false) String labStatus,
            @RequestParam(name = "radStatus", required = false) String radStatus,

            @RequestParam(name = "submittedDateFrom", required = false) Instant submittedDateFrom,
            @RequestParam(name = "submittedDateTo", required = false) Instant submittedDateTo,

            @RequestParam(name = "departmentId", required = false) Long departmentId,
            @RequestParam(name = "testType", required = false) TestType testType,

            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrder] FILTER - request received. patientId={} encounterId={} status={} statusIn={} statusNotIn={} excludeStatus={} saveDraft={} isUrgent={} labStatus={} radStatus={} submittedDateFrom={} submittedDateTo={} departmentId={} testType={} pageable={}",
                patientId, encounterId, status, statusIn, statusNotIn, excludeStatus, saveDraft, isUrgent, labStatus,
                radStatus, submittedDateFrom, submittedDateTo, departmentId, testType, pageable);

        if (status != null && statusIn != null && !statusIn.isEmpty()) {
            throw new BadRequestAlertException(
                    "invalid_filter",
                    "diagnostic_orders",
                    "Use either status or statusIn, not both"
            );
        }

        Specification<DiagnosticOrder> filterSpec = (orderRoot, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> filterPredicates = new ArrayList<>();

            if (patientId != null) filterPredicates.add(criteriaBuilder.equal(orderRoot.get("patientId"), patientId));
            if (encounterId != null) filterPredicates.add(criteriaBuilder.equal(orderRoot.get("encounterId"), encounterId));

            if (status != null) filterPredicates.add(criteriaBuilder.equal(orderRoot.get("status"), status));
            if (statusIn != null && !statusIn.isEmpty()) filterPredicates.add(orderRoot.get("status").in(statusIn));
            if (excludeStatus != null)
                filterPredicates.add(criteriaBuilder.notEqual(orderRoot.get("status"), excludeStatus));
            if (statusNotIn != null && !statusNotIn.isEmpty())
                filterPredicates.add(criteriaBuilder.not(orderRoot.get("status").in(statusNotIn)));

            if (saveDraft != null) filterPredicates.add(criteriaBuilder.equal(orderRoot.get("saveDraft"), saveDraft));
            if (isUrgent != null) filterPredicates.add(criteriaBuilder.equal(orderRoot.get("isUrgent"), isUrgent));

            if (labStatus != null && !labStatus.isBlank())
                filterPredicates.add(criteriaBuilder.equal(orderRoot.get("labStatus"), labStatus));
            if (radStatus != null && !radStatus.isBlank())
                filterPredicates.add(criteriaBuilder.equal(orderRoot.get("radStatus"), radStatus));

            if (submittedDateFrom != null)
                filterPredicates.add(criteriaBuilder.greaterThanOrEqualTo(orderRoot.get("submittedDate"), submittedDateFrom));
            if (submittedDateTo != null)
                filterPredicates.add(criteriaBuilder.lessThanOrEqualTo(orderRoot.get("submittedDate"), submittedDateTo));

            if (departmentId != null) {
                Subquery<Long> orderTestSubquery = criteriaQuery.subquery(Long.class);
                Root<DiagnosticOrderTest> orderTestRoot = orderTestSubquery.from(DiagnosticOrderTest.class);

                List<Predicate> subqueryPredicates = new ArrayList<>();
                subqueryPredicates.add(criteriaBuilder.equal(orderTestRoot.get("orderId"), orderRoot.get("id")));
                subqueryPredicates.add(criteriaBuilder.equal(orderTestRoot.get("receivedDepartmentId"), departmentId));
                subqueryPredicates.add(criteriaBuilder.notEqual(orderTestRoot.get("status"), DiagnosticOrderTestStatus.CANCELLED));

                if (testType != null) {
                    subqueryPredicates.add(criteriaBuilder.equal(orderTestRoot.get("orderType"), testType));
                }

                orderTestSubquery.select(orderTestRoot.get("id")).where(subqueryPredicates.toArray(new Predicate[0]));
                filterPredicates.add(criteriaBuilder.exists(orderTestSubquery));
            }

            return criteriaBuilder.and(filterPredicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticOrder> ordersPage = diagnosticOrderRepository.findAll(filterSpec, pageable);

        HttpHeaders paginationHeaders = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), ordersPage
        );

        List<DiagnosticOrderResponseVM> responseBody = ordersPage.getContent()
                .stream()
                .map(DiagnosticOrderResponseVM::ofEntity)
                .toList();

        LOG.debug("[DiagnosticOrder] FILTER - response ready. returned={} totalElements={} totalPages={}",
                ordersPage.getNumberOfElements(), ordersPage.getTotalElements(), ordersPage.getTotalPages());

        return new ResponseEntity<>(responseBody, paginationHeaders, HttpStatus.OK);
    }


    @PostMapping("/diagnostic-orders/{id}/submit")
    public ResponseEntity<DiagnosticOrderResponseVM> submit(@Valid @PathVariable Long id) {
        LOG.debug("[DiagnosticOrder] SUBMIT - request received. id={}", id);

        DiagnosticOrder order = diagnosticOrderRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + id
                ));

        if (Boolean.FALSE.equals(order.getSaveDraft())) {
            throw new BadRequestAlertException(
                    "already_submitted",
                    "diagnostic_orders",
                    "Order already submitted"
            );
        }

        String username = currentUsername();

        DiagnosticOrder submittedOrder = diagnosticOrderService.submit(order, username);

        LOG.debug("[DiagnosticOrder] SUBMIT - submitted successfully. id={}", submittedOrder.getId());
        return ResponseEntity.ok(DiagnosticOrderResponseVM.ofEntity(submittedOrder));
    }

}

