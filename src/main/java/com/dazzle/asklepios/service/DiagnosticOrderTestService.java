package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestUpdateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for managing {@link DiagnosticOrderTest} entities.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Create new diagnostic order test records.</li>
 *   <li>Update existing diagnostic order test records.</li>
 *   <li>Query diagnostic order tests by orderId with optional status filters.</li>
 *   <li>Delete diagnostic order test records.</li>
 * </ul>
 */
@Service
@Transactional
public class DiagnosticOrderTestService {

    /**
     * Logger for debugging and tracing service operations.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestService.class);

    /**
     * Repository for CRUD operations on DiagnosticOrderTest.
     */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    /**
     * Service responsible for recomputing/maintaining overall diagnostic order statuses.
     */
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    /**
     * Creates the service with required dependencies.
     *
     * @param diagnosticOrderTestRepository repository used to persist and query DiagnosticOrderTest
     * @param diagnosticOrderStatusService  service used to recompute aggregated lab/radiology statuses
     */
    public DiagnosticOrderTestService(
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService
    ) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    /**
     * Creates and persists a new {@link DiagnosticOrderTest} from the provided DTO.
     * <p>
     * Default behavior:
     * <ul>
     *   <li>Sets {@link DiagnosticOrderTestStatus#NEW} as the initial test status.</li>
     *   <li>If processingStatus is not provided, defaults to {@link DiagnosticStatus#NEW}.</li>
     *   <li>After saving, recomputes lab/radiology statuses for the parent order.</li>
     * </ul>
     *
     * @param dto payload containing creation fields
     * @return persisted DiagnosticOrderTest entity
     */
    public DiagnosticOrderTest create(DiagnosticOrderTestCreateDTO dto) {
        LOG.debug("Request to create DiagnosticOrderTest: {}", dto);

        // Build a new entity instance from DTO fields
        DiagnosticOrderTest t = new DiagnosticOrderTest();
        t.setPatientId(dto.patientId());
        t.setEncounterId(dto.encounterId());
        t.setOrderId(dto.orderId());
        t.setTestId(dto.testId());

        // Initial lifecycle status for a newly created order test
        t.setStatus(DiagnosticOrderTestStatus.NEW);

        // Set processing status if provided; otherwise default to NEW
        if (dto.processingStatus() != null) {
            t.setProcessingStatus(dto.processingStatus());
        } else {
            t.setProcessingStatus(DiagnosticStatus.NEW);
        }

        // Additional metadata and routing information
        t.setReceivedDepartmentId(dto.receivedDepartmentId());
        t.setReason(dto.reason());
        t.setNotes(dto.notes());
        t.setSubmitDate(dto.submitDate());
        t.setOrderType(dto.orderType());

        // Persist the entity
        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(t);

        // Recompute aggregated statuses for the parent diagnostic order (lab/rad)
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    /**
     * Updates an existing {@link DiagnosticOrderTest} entity using the provided DTO and persists changes.
     * <p>
     * Notes:
     * <ul>
     *   <li>This method updates core identifiers and routing fields.</li>
     *   <li>It does not update status/processingStatus or submitDate/orderType here.</li>
     * </ul>
     *
     * @param existing the already-loaded entity to be updated
     * @param dto      payload containing updated fields
     * @return persisted (updated) DiagnosticOrderTest entity
     */
    public DiagnosticOrderTest update(DiagnosticOrderTest existing, DiagnosticOrderTestUpdateDTO dto) {
        LOG.debug("Request to update DiagnosticOrderTest id={} payload={}", existing.getId(), dto);

        // Update core references
        existing.setPatientId(dto.patientId());
        existing.setEncounterId(dto.encounterId());
        existing.setOrderId(dto.orderId());
        existing.setTestId(dto.testId());

        // Update request details
        existing.setReceivedDepartmentId(dto.receivedDepartmentId());
        existing.setReason(dto.reason());
        existing.setNotes(dto.notes());

        // Persist the updated entity
        return diagnosticOrderTestRepository.save(existing);
    }

    /**
     * Retrieves {@link DiagnosticOrderTest} records for a given orderId with optional status filtering.
     * <p>
     * Filtering rules (in priority order):
     * <ol>
     *   <li>If {@code status} is provided: return items matching that status.</li>
     *   <li>Else if {@code excludeStatuses} is provided and not empty: return items NOT in that list.</li>
     *   <li>Else: return all items by orderId.</li>
     * </ol>
     *
     * @param orderId         parent diagnostic order identifier
     * @param status          optional exact status filter (highest priority)
     * @param excludeStatuses optional list of statuses to exclude if {@code status} is null
     * @param pageable        pagination and sorting information
     * @return paged results matching the filter criteria
     */
    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTest> findByOrderIdFilterStatus(
            Long orderId,
            DiagnosticOrderTestStatus status,
            List<DiagnosticOrderTestStatus> excludeStatuses,
            Pageable pageable
    ) {
        // Exact status filter has priority over exclude list
        if (status != null) {
            return diagnosticOrderTestRepository.findByOrderIdAndStatus(orderId, status, pageable);
        }

        // Exclude statuses when provided
        if (excludeStatuses != null && !excludeStatuses.isEmpty()) {
            return diagnosticOrderTestRepository.findByOrderIdAndStatusNotIn(orderId, excludeStatuses, pageable);
        }

        // Default: return all tests for the order
        return diagnosticOrderTestRepository.findByOrderId(orderId, pageable);
    }

    /**
     * Deletes a {@link DiagnosticOrderTest} by its identifier.
     *
     * @param id entity identifier
     */
    public void delete(Long id) {
        diagnosticOrderTestRepository.deleteById(id);
    }
}
