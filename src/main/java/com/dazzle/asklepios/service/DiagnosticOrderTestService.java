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
 *   <li>Query diagnostic order tests by orderId, by orderId + status, or excluding statuses.</li>
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
        DiagnosticOrderTest orderTest = new DiagnosticOrderTest();
        orderTest.setOrderId(dto.orderId());
        orderTest.setTestId(dto.testId());

        // Initial lifecycle status for a newly created order test
        orderTest.setStatus(DiagnosticOrderTestStatus.NEW);

        // Set processing status if provided; otherwise default to NEW

            orderTest.setProcessingStatus(DiagnosticStatus.NEW);


        // Additional metadata and routing information
        orderTest.setReceivedDepartmentId(dto.receivedDepartmentId());
        orderTest.setReason(dto.reason());
        orderTest.setNotes(dto.notes());
        orderTest.setOrderType(dto.orderType());

        // Persist the entity
        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(orderTest);

        LOG.debug("[DiagnosticOrderTestService] CREATE - saved. id={} orderId={} testId={} status={} processingStatus={}",
                saved.getId(), saved.getOrderId(), saved.getTestId(), saved.getStatus(), saved.getProcessingStatus());
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestService] CREATE - recompute status done. orderId={}", saved.getOrderId());

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

        existing.setOrderId(dto.orderId());
        existing.setTestId(dto.testId());

        // Update request details
        existing.setReceivedDepartmentId(dto.receivedDepartmentId());
        existing.setReason(dto.reason());
        existing.setNotes(dto.notes());

        // Persist the updated entity
        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(existing);
        LOG.debug("[DiagnosticOrderTestService] UPDATE - done. id={} orderId={} testId={}",
                saved.getId(), saved.getOrderId(), saved.getTestId());
        return saved;
    }

    /**
     * Retrieves {@link DiagnosticOrderTest} records for a given orderId.
     *
     * @param orderId  parent diagnostic order identifier
     * @param pageable pagination and sorting information
     * @return paged results for the order
     */
    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTest> findByOrderId(Long orderId, Pageable pageable) {
        LOG.debug("[DiagnosticOrderTestService] FIND_BY_ORDER - start. orderId={} pageable={}", orderId, pageable);
        Page<DiagnosticOrderTest> page = diagnosticOrderTestRepository.findByOrderId(orderId, pageable);
        LOG.debug("[DiagnosticOrderTestService] FIND_BY_ORDER - done. orderId={} returned={} totalElements={} totalPages={}",
                orderId, page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return page;
    }

    /**
     * Retrieves {@link DiagnosticOrderTest} records for a given orderId with an exact status filter.
     *
     * @param orderId  parent diagnostic order identifier
     * @param status   exact status to include
     * @param pageable pagination and sorting information
     * @return paged results matching the status
     */
    public Page<DiagnosticOrderTest> findByOrderIdAndStatus(
            Long orderId,
            DiagnosticOrderTestStatus status,
            Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrderTestService] FIND_BY_ORDER_AND_STATUS - start. orderId={} status={} pageable={}",
                orderId, status, pageable);
        Page<DiagnosticOrderTest> page = diagnosticOrderTestRepository.findByOrderIdAndStatus(orderId, status, pageable);
        LOG.debug("[DiagnosticOrderTestService] FIND_BY_ORDER_AND_STATUS - done. orderId={} status={} returned={} totalElements={} totalPages={}",
                orderId, status, page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return page;
    }

    /**
     * Retrieves {@link DiagnosticOrderTest} records for a given orderId excluding provided statuses.
     *
     * @param orderId          parent diagnostic order identifier
     * @param excludeStatuses  list of statuses to exclude
     * @param pageable         pagination and sorting information
     * @return paged results excluding the provided statuses
     */
    public Page<DiagnosticOrderTest> findByOrderIdExcludingStatuses(
            Long orderId,
            List<DiagnosticOrderTestStatus> excludeStatuses,
            Pageable pageable
    ) {
        LOG.debug("[DiagnosticOrderTestService] FIND_BY_ORDER_EXCLUDING - start. orderId={} excludeStatuses={} pageable={}",
                orderId, excludeStatuses, pageable);
        Page<DiagnosticOrderTest> page = diagnosticOrderTestRepository.findByOrderIdAndStatusNotIn(
                orderId, excludeStatuses, pageable
        );
        LOG.debug("[DiagnosticOrderTestService] FIND_BY_ORDER_EXCLUDING - done. orderId={} returned={} totalElements={} totalPages={}",
                orderId, page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return page;
    }

    /**
     * Deletes a {@link DiagnosticOrderTest} by its identifier.
     *
     * @param id entity identifier
     */
    public void delete(Long id) {
        LOG.debug("[DiagnosticOrderTestService] DELETE - start. id={}", id);
        diagnosticOrderTestRepository.deleteById(id);
        LOG.debug("[DiagnosticOrderTestService] DELETE - done. id={}", id);
    }
}
