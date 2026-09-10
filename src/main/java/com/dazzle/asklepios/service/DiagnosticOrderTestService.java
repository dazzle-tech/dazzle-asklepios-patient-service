package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestTechnicianNoteRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.DiagnosticTestHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.ICDTreeHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderTestResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

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

    private final DiagnosticOrderTestTechnicianNoteRepository diagnosticOrderTestTechnicianNoteRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticTestHelper diagnosticTestHelper;
    private final DepartmentHelper departmentHelper;
    private final ICDTreeHelper icdTreeHelper;
    private final FacilityHelper facilityHelper;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    private final BillingRuleEvaluationService billingRuleEvaluationService;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;

    /**
     * Creates the service with required dependencies.
     *
     * @param diagnosticOrderTestRepository repository used to persist and query DiagnosticOrderTest
     * @param diagnosticOrderStatusService  service used to recompute aggregated lab/radiology statuses
     */
    public DiagnosticOrderTestService(
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService,
            DiagnosticOrderTestTechnicianNoteRepository diagnosticOrderTestTechnicianNoteRepository,
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticTestHelper diagnosticTestHelper,
            DepartmentHelper departmentHelper,
            ICDTreeHelper icdTreeHelper, FacilityHelper facilityHelper,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            BillingRuleEvaluationService billingRuleEvaluationService,
            @org.springframework.context.annotation.Lazy DiagnosticOrderTestStatusService diagnosticOrderTestStatusService
    ) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;

        this.diagnosticOrderTestTechnicianNoteRepository = diagnosticOrderTestTechnicianNoteRepository;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticTestHelper = diagnosticTestHelper;
        this.departmentHelper = departmentHelper;
        this.icdTreeHelper = icdTreeHelper;
        this.facilityHelper = facilityHelper;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.billingRuleEvaluationService = billingRuleEvaluationService;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
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
        DiagnosticOrder order = getDiagnosticOrder(dto.orderId());
        diagnosticTestHelper.getDiagnosticTest(dto.testId());
        Long finalReceivedDepartmentId;
        if (dto.receivedDepartmentId() != null) {
            finalReceivedDepartmentId = dto.receivedDepartmentId();
            departmentHelper.validateDepartmentExists(dto.receivedDepartmentId());
        } else {
            FacilityDTO facilityDTO = facilityHelper.getFacility(order.getFromFacilityId());

            finalReceivedDepartmentId = dto.orderType() == TestType.LABORATORY ? facilityDTO.defaultLabDepartmentId() : dto.orderType() == TestType.RADIOLOGY ? facilityDTO.defaultRadDepartmentId() : null;
        }
        if (dto.icdDiagnosisId() != null)
            icdTreeHelper.validateICDDiagnosisExists(dto.icdDiagnosisId());

        billingRuleEvaluationService.requireConfiguredRule(
                resolveDiagnosticBillingItemType(dto.orderType()),
                dto.testId(),
                BillingEventType.ITEM_ORDERED
        );

        DiagnosticOrderTest orderTest = new DiagnosticOrderTest();
        orderTest.setOrderId(order.getId());
        orderTest.setTestId(dto.testId());

        // Initial lifecycle status for a newly created order test
        orderTest.setStatus(DiagnosticOrderTestStatus.NEW);

        // Set processing status if provided; otherwise default to NEW

        orderTest.setProcessingStatus(DiagnosticStatus.NEW);


        // Additional metadata and routing information
        orderTest.setReceivedDepartmentId(finalReceivedDepartmentId);
        orderTest.setReason(dto.reason());
        orderTest.setNotes(dto.notes());
        orderTest.setOrderType(dto.orderType());
        orderTest.setIcdDiagnosisId(dto.icdDiagnosisId());

        // Persist the entity
        DiagnosticOrderTest saved = diagnosticOrderTestRepository.saveAndFlush(orderTest);

        LOG.debug("[DiagnosticOrderTestService] CREATE - saved. id={} orderId={} testId={} status={} processingStatus={}",
                saved.getId(), saved.getOrderId(), saved.getTestId(), saved.getStatus(), saved.getProcessingStatus());

        // Create billing + submit pre-auth immediately when the test is added (not on accept).
        diagnosticOrderTestStatusService.onTestAddedToOrder(saved, dto.acceptUncoveredAsCash());

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
        DiagnosticOrder order = getDiagnosticOrder(dto.orderId());
        diagnosticTestHelper.getDiagnosticTest(dto.testId());
        if (dto.receivedDepartmentId() != null)
            departmentHelper.validateDepartmentExists(dto.receivedDepartmentId());
        if (dto.icdDiagnosisId() != null)
            icdTreeHelper.validateICDDiagnosisExists(dto.icdDiagnosisId());
        existing.setOrderId(order.getId());
        existing.setTestId(dto.testId());

        // Update request details
        existing.setReceivedDepartmentId(dto.receivedDepartmentId());
        existing.setReason(dto.reason());
        existing.setNotes(dto.notes());
        existing.setIcdDiagnosisId(dto.icdDiagnosisId());


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
     * @param orderId         parent diagnostic order identifier
     * @param excludeStatuses list of statuses to exclude
     * @param pageable        pagination and sorting information
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

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTestResponseVM> filterDiagnosticOrderTests(
            Specification<DiagnosticOrderTest> spec,
            Pageable pageable
    ) {

        Page<DiagnosticOrderTest> page =
                diagnosticOrderTestRepository.findAll(spec, pageable);

        List<Long> orderTestIds = page.getContent()
                .stream()
                .map(DiagnosticOrderTest::getId)
                .toList();

        Set<Long> idsWithNotes =
                new HashSet<>(
                        diagnosticOrderTestTechnicianNoteRepository
                                .findDistinctOrderTestIdsIn(orderTestIds)
                );

        return page.map(orderTest -> {

            boolean hasNote = idsWithNotes.contains(orderTest.getId());

            return DiagnosticOrderTestResponseVM
                    .ofEntityWithNote(orderTest, hasNote);
        });
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

    private DiagnosticOrder getDiagnosticOrder(Long diagnosticOrderId) {
        LOG.debug("[TechnicianNoteService]  getDiagnosticOrder:  id={}", diagnosticOrderId);

        return diagnosticOrderRepository.findById(diagnosticOrderId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "Order not found with id " + diagnosticOrderId
                ));
    }

    private BillingItemTypes resolveDiagnosticBillingItemType(
            TestType orderType
    ) {
        if (orderType == TestType.RADIOLOGY) {
            return BillingItemTypes.RADIOLOGY;
        }

        if (orderType == TestType.PATHOLOGY) {
            return BillingItemTypes.PATHOLOGY;
        }

        return BillingItemTypes.LABORATORY;
    }

    private ServiceSource resolveDiagnosticServiceSource(TestType orderType) {
        return orderType == TestType.RADIOLOGY
                ? ServiceSource.RADIOLOGY
                : ServiceSource.LABORATORY;
    }

    @Transactional(readOnly = true)
    public boolean isDiagnosticOrderPaid(Long orderTestId) {
        if (orderTestId == null) {
            throw new BadRequestAlertException(
                    "diagnosticOrderTestId is required",
                    "diagnostic_order_tests",
                    "id.required"
            );
        }

        DiagnosticOrderTest orderTest = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        BillingItemTypes billingItemType =
                orderTest.getOrderType() == TestType.RADIOLOGY
                        ? BillingItemTypes.RADIOLOGY
                        : BillingItemTypes.LABORATORY;

        return patientServiceAndProductRepository
                .findByServiceSourceAndSourceIdAndBillingItemType(
                        resolveDiagnosticServiceSource(orderTest.getOrderType()),
                        orderTest.getId(),
                        billingItemType
                )
                .map(item -> {
                    if (item.getPaymentStatus() == PaymentStatus.CANCELLED) {
                        return false;
                    }

                    BigDecimal remainingAmount =
                            item.getRemainingAmount() == null
                                    ? ZERO_AMOUNT
                                    : item.getRemainingAmount();

                    return remainingAmount.compareTo(ZERO_AMOUNT) <= 0;
                })
                .orElse(false);
    }

    void validateSettlementTestBeforeApprove(long orderTestId) {

        Long facilityId = getFacility();
        FacilityDTO facilityDTO = facilityHelper.getFacility(facilityId);

        if (!Boolean.TRUE.equals(facilityDTO.approvingDiagnosticTestSettlePayment())) {
            return;
        }

        boolean paid = isDiagnosticOrderPaid(orderTestId);
        if (!paid) {
            throw new BadRequestAlertException(
                    "payment_required",
                    "diagnostic_order_test",
                    "Diagnostic test must be fully settled before approval"
            );
        }
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

}
