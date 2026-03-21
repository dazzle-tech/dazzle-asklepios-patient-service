package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for maintaining aggregated (group-level) statuses on {@link DiagnosticOrder}.
 * <p>
 * The order contains two aggregated statuses:
 * <ul>
 *   <li>Lab status (for tests of type {@link TestType#LABORATORY})</li>
 *   <li>Radiology status (for tests of type {@link TestType#RADIOLOGY})</li>
 * </ul>
 * <p>
 * The aggregation logic is based on distinct processing statuses found in the order's tests:
 * <ul>
 *   <li>No tests (or no statuses) -> {@link DiagnosticStatus#NEW}</li>
 *   <li>All tests share the same status -> that status</li>
 *   <li>Multiple different statuses -> {@link DiagnosticStatus#PARTIALLY}</li>
 * </ul>
 */
@Service
@Transactional
public class DiagnosticOrderStatusService {

    /** Repository used to query test processing statuses for an order by test type. */
    private final DiagnosticOrderTestRepository testRepository;

    /** Repository used to load and persist DiagnosticOrder entities. */
    private final DiagnosticOrderRepository orderRepository;

    /**
     * Creates the service with required dependencies.
     *
     * @param testRepository repository used to query DiagnosticOrderTest processing statuses
     * @param orderRepository repository used to load/save DiagnosticOrder
     */
    public DiagnosticOrderStatusService(
            DiagnosticOrderTestRepository testRepository,
            DiagnosticOrderRepository orderRepository
    ) {
        this.testRepository = testRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Recomputes and persists aggregated Lab/Radiology statuses for a given order.
     * <p>
     * Steps:
     * <ol>
     *   <li>Load the parent {@link DiagnosticOrder}.</li>
     *   <li>Resolve lab status from laboratory tests.</li>
     *   <li>Resolve radiology status from radiology tests.</li>
     *   <li>Update the order and save.</li>
     * </ol>
     *
     * @param orderId id of the DiagnosticOrder
     * @throws BadRequestAlertException if the order is not found
     */
    public void recomputeLabRadStatuses(Long orderId) {
        DiagnosticOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "DiagnosticOrder not found with id " + orderId
                ));

        // Compute group-level statuses for each test category
        DiagnosticStatus labStatus = resolveGroupStatus(orderId, TestType.LABORATORY);
        DiagnosticStatus radStatus = resolveGroupStatus(orderId, TestType.RADIOLOGY);

        // Update aggregated statuses on the order
        order.setLabStatus(labStatus);
        order.setRadStatus(radStatus);

        // Persist changes
        orderRepository.save(order);
    }

    /**
     * Resolves an aggregated status for all tests under an order for a given {@link TestType}.
     * <p>
     * Uses the distinct processing statuses returned by the repository:
     * <ul>
     *   <li>Empty -> NEW</li>
     *   <li>Single distinct status -> that status</li>
     *   <li>Multiple distinct statuses -> PARTIAL</li>
     * </ul>
     *
     * @param orderId parent diagnostic order id
     * @param type test type to aggregate (LABORATORY or RADIOLOGY)
     * @return aggregated status for that test type
     */
    private DiagnosticStatus resolveGroupStatus(Long orderId, TestType type) {
        // Get distinct processing statuses for tests of the given type in this order
        List<DiagnosticStatus> distinct = testRepository.findDistinctProcessingStatuses(orderId, type);

        // No tests (or no statuses) -> default NEW
        if (distinct == null || distinct.isEmpty()) {
            return DiagnosticStatus.NEW;
        }

        // All tests share the same processing status -> return it
        if (distinct.size() == 1) {
            return distinct.get(0);
        }

        // Mixed statuses across tests -> PARTIAL
        return DiagnosticStatus.PARTIALLY;
    }
}
