package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.DiagnosticTestProfileClient;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.LabResultLog;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.NormalRangeType;
import com.dazzle.asklepios.domain.enumeration.Severity;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultTechnicianNoteRepository;
import com.dazzle.asklepios.repository.LabResultLogRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.ApproveResultDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultResponseVM;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultResultsVM;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class DiagnosticOrderTestResultService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticOrderTestResultService.class);

    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;
    private final DiagnosticOrderTestResultStatusService orderTestResultStatusService;
    private final DiagnosticOrderTestRepository orderTestRepository;
    private final DiagnosticOrderRepository orderRepository;
    private final LabResultLogRepository labResultLogRepository;
    private final NormalRangeMatcherService normalRangeMatcherService;
    private final DiagnosticOrderTestResultTechnicianNoteRepository diagnosticOrderTestResultTechnicianNoteRepository;
    private final DiagnosticTestProfileClient diagnosticTestProfileClient;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final PatientRepository patientRepository;
    private final DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository;
    private final DepartmentHelper departmentHelper;
    private final NotificationHelper notificationHelper;
    private final DiagnosticOrderTestService diagnosticOrderTestService;

    private void validateResultValue(
            Long profileTestId,
            BigDecimal resultValueNumber,
            String resultValueText
    ) {

        TestResultType resultType;

        try {
            resultType =
                    diagnosticTestProfileClient
                            .getResultTypeByProfileTestIdInternal(
                                    profileTestId
                            );

        } catch (Exception e) {

            throw new BadRequestAlertException(
                    "setup_service_error",
                    "diagnostic_order_tests_result",
                    "Failed to fetch result type for profileTestId "
                            + profileTestId
            );
        }

        switch (resultType) {

            case NUMBER -> {

                if (resultValueNumber == null) {

                    throw new BadRequestAlertException(
                            "number_result_required",
                            "diagnostic_order_tests_result",
                            "Result value number is required"
                    );
                }
            }

            case LOV -> {

                if (resultValueText == null
                        || resultValueText.isBlank()) {

                    throw new BadRequestAlertException(
                            "lov_result_required",
                            "diagnostic_order_tests_result",
                            "Result LOV value is required"
                    );
                }
            }

            case TEXT -> {

                if (resultValueText == null
                        || resultValueText.isBlank()) {

                    throw new BadRequestAlertException(
                            "text_result_required",
                            "diagnostic_order_tests_result",
                            "Result text value is required"
                    );
                }
            }
        }
    }

    /**
     * Creates and persists a new {@link DiagnosticOrderTestResult}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>Maps DTO fields into a new entity.</li>
     *   <li>Sets {@link DiagnosticStatus#RESULT_READY} as the initial processing status.</li>
     *   <li>Saves the entity.</li>
     *   <li>Triggers recomputation of the parent test processing status from its results.</li>
     * </ul>
     *
     * @param testResultCreateDTO create payload
     * @return persisted {@link DiagnosticOrderTestResult}
     */

    public DiagnosticOrderTestResult create(DiagnosticOrderTestResultCreateDTO testResultCreateDTO) {
        LOG.debug("[DiagnosticOrderTestResultService] CREATE - start. payload={}", testResultCreateDTO);
        validateResultValue(
                testResultCreateDTO.profileTestId(),
                testResultCreateDTO.resultValueNumber(),
                testResultCreateDTO.resultValueText()
        );
        DiagnosticOrderTestResult result = new DiagnosticOrderTestResult();
        result.setOrderTestId(testResultCreateDTO.orderTestId());
        result.setProfileTestId(testResultCreateDTO.profileTestId());
        result.setResultValueNumber(testResultCreateDTO.resultValueNumber());
        result.setResultValueText(testResultCreateDTO.resultValueText());
        result.setMarker(testResultCreateDTO.marker());
        result.setNormalRangeValue(testResultCreateDTO.normalRangeValue());
        result.setProcessingStatus(DiagnosticStatus.RESULT_READY);

        DiagnosticOrderTestResult saved = diagnosticOrderTestResultRepository.save(result);

        orderTestResultStatusService.recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestResultService] CREATE - done. id={} orderTestId={} profileTestId={} processingStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getProfileTestId(), saved.getProcessingStatus());

        return saved;
    }

    @Transactional
    public void createBulk(
            List<DiagnosticOrderTestResultCreateDTO> dtos
    ) {

        LOG.debug(
                "[DiagnosticOrderTestResultService] BULK CREATE - count={}",
                dtos.size()
        );

        List<Long> affectedOrderTests = new ArrayList<>();

        for (DiagnosticOrderTestResultCreateDTO dto : dtos) {

            validateResultValue(
                    dto.profileTestId(),
                    dto.resultValueNumber(),
                    dto.resultValueText()
            );

            DiagnosticOrderTestResult result =
                    new DiagnosticOrderTestResult();

            result.setOrderTestId(dto.orderTestId());
            result.setProfileTestId(dto.profileTestId());
            result.setResultValueNumber(dto.resultValueNumber());
            result.setResultValueText(dto.resultValueText());
            result.setMarker(dto.marker());
            result.setNormalRangeValue(dto.normalRangeValue());
            result.setProcessingStatus(DiagnosticStatus.RESULT_READY);

            diagnosticOrderTestResultRepository.save(result);

            affectedOrderTests.add(dto.orderTestId());
        }

        affectedOrderTests.stream()
                .distinct()
                .forEach(
                        orderTestResultStatusService
                                ::recomputeTestProcessingStatusFromResults
                );
    }

    public DiagnosticOrderTestResult updateWithValidation(
            Long id,
            DiagnosticOrderTestResultUpdateDTO testResultUpdateDTO
    ) {

        DiagnosticOrderTestResult testResult = diagnosticOrderTestResultRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));
        validateResultValue(
                testResultUpdateDTO.profileTestId(),
                testResultUpdateDTO.resultValueNumber(),
                testResultUpdateDTO.resultValueText()
        );
        testResult.setOrderTestId(testResultUpdateDTO.orderTestId());
        testResult.setProfileTestId(testResultUpdateDTO.profileTestId());
        testResult.setResultValueNumber(testResultUpdateDTO.resultValueNumber());
        testResult.setResultValueText(testResultUpdateDTO.resultValueText());
        testResult.setMarker(testResultUpdateDTO.marker());
        testResult.setNormalRangeValue(testResultUpdateDTO.normalRangeValue());

        return diagnosticOrderTestResultRepository.save(testResult);
    }

    /**
     * Approve a single result using full business logic.
     */
    @Transactional
    public DiagnosticOrderTestResult approveResult(Long resultId, String approvedBy) {

        DiagnosticOrderTestResult result = diagnosticOrderTestResultRepository.findById(resultId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + resultId
                ));
        diagnosticOrderTestService.validateSettlementTestBeforeApprove(
                result.getOrderTestId()
        );
        Long patientId = resolvePatientId(result.getOrderTestId());

        TestResultMarker viewMarker = result.getMarker();
        String viewNormalRange = result.getNormalRangeValue();

        TestResultType resultType;
        try {
            resultType = diagnosticTestProfileClient.getResultTypeByProfileTestIdInternal(result.getProfileTestId());
        } catch (Exception e) {
            throw new BadRequestAlertException(
                    "setup_service_error",
                    "diagnostic_order_tests_result",
                    "Failed to fetch result type for profileTestId " + result.getProfileTestId()
            );
        }

        if (patientId != null) {
            NormalRangeMatchDTO bestNormalRange =
                    normalRangeMatcherService.findBestNormalRange(result.getProfileTestId(), patientId);

            viewMarker = NormalRangeMatcherService.calculateMarker(
                    resultType,
                    result.getResultValueNumber(),
                    result.getResultValueText(),
                    bestNormalRange
            );

            viewNormalRange = buildViewNormalRange(bestNormalRange);
        }

        DiagnosticOrderTestResult approvedResult =
                orderTestResultStatusService.approve(
                        new ApproveResultDTO(
                                resultId,
                                approvedBy,
                                viewMarker,
                                viewNormalRange
                        )
                );

        notificationForResultReady(
                approvedResult,
                viewMarker
        );

        notificationForCriticalLaboratoryResultReady(
                approvedResult,
                viewMarker
        );

        return approvedResult;
    }

    /**
     * Bulk approve results using the same business logic as single approve.
     *
     * @param resultIds  list of result ids
     * @param approvedBy current username
     */
    public void bulkApproveResults(List<Long> resultIds, String approvedBy) {
        LOG.debug("[DiagnosticOrderTestResultService] BULK_APPROVE - start count={} ids={} approvedBy={}",
                resultIds.size(), resultIds, approvedBy);

        for (Long id : resultIds) {
            approveResult(id, approvedBy);
        }

        LOG.debug("[DiagnosticOrderTestResultService] BULK_APPROVE - done count={}", resultIds.size());
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTestResultResponseVM> resultFilter(
            Specification<DiagnosticOrderTestResult> spec,
            Pageable pageable
    ) {

        Page<DiagnosticOrderTestResult> page =
                diagnosticOrderTestResultRepository.findAll(spec, pageable);

        return page.map(result -> {

            Long patientId =
                    resolvePatientId(result.getOrderTestId());

            TestResultMarker viewMarker = TestResultMarker.UNKNOWN;
            String viewNormalRange = " ";

            TestResultType resultType = null;

            try {
                resultType = diagnosticTestProfileClient
                        .getResultTypeByProfileTestIdInternal(
                                result.getProfileTestId()
                        );
            } catch (Exception ignored) {
                LOG.warn("ResultType fetch failed for profileTestId={}",
                        result.getProfileTestId());
            }

            if (patientId != null && resultType != null) {

                NormalRangeMatchDTO best =
                        normalRangeMatcherService.findBestNormalRange(
                                result.getProfileTestId(),
                                patientId
                        );

                viewMarker = NormalRangeMatcherService.calculateMarker(
                        resultType,
                        result.getResultValueNumber(),
                        result.getResultValueText(),
                        best
                );

                viewNormalRange = buildViewNormalRange(best);
            }
            boolean hasNote =
                    diagnosticOrderTestResultTechnicianNoteRepository.existsByResultId(
                            result.getId()
                    );

            return DiagnosticOrderTestResultResponseVM
                    .ofEntityWithViewNote(result, viewMarker, viewNormalRange, hasNote);
        });
    }

    @Transactional(readOnly = true)
    public List<Long> resultFilterIds(
            Specification<DiagnosticOrderTestResult> specification
    ) {

        return diagnosticOrderTestResultRepository
                .findAll(specification)
                .stream()
                .map(DiagnosticOrderTestResult::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTestResultResultsVM> resultsPage(
            Specification<DiagnosticOrderTestResult> spec,
            Pageable pageable
    ) {

        Page<DiagnosticOrderTestResult> page =
                diagnosticOrderTestResultRepository.findAll(spec, pageable);

        return page.map(result -> {

            // =====================================================
            // ORDER TEST
            // =====================================================

            DiagnosticOrderTest orderTest =
                    diagnosticOrderTestRepository
                            .findById(result.getOrderTestId())
                            .orElse(null);

            if (orderTest == null) {
                return new DiagnosticOrderTestResultResultsVM(
                        result.getId(),
                        result.getOrderTestId(),
                        null,
                        result.getProfileTestId(),

                        result.getResultValueNumber(),
                        result.getResultValueText(),

                        result.getMarker(),
                        result.getMarker(),
                        result.getNormalRangeValue(),

                        result.getCreatedDate(),

                        null,
                        null,

                        result.getCreatedBy(),
                        result.getCreatedDate(),

                        null,

                        diagnosticOrderTestResultTechnicianNoteRepository
                                .existsByResultId(result.getId()),

                        false
                );
            }

            // =====================================================
            // ORDER
            // =====================================================

            DiagnosticOrder order =
                    diagnosticOrderRepository
                            .findById(orderTest.getOrderId())
                            .orElse(null);

            // =====================================================
            // PATIENT
            // =====================================================

            Patient patient = null;

            if (order != null && order.getPatientId() != null) {
                patient = patientRepository
                        .findById(order.getPatientId())
                        .orElse(null);
            }

            String patientName = null;
            String mrn = null;

            if (patient != null) {

                patientName = String.join(
                        " ",
                        patient.getFirstName() != null
                                ? patient.getFirstName()
                                : "",
                        patient.getSecondName() != null
                                ? patient.getSecondName()
                                : "",
                        patient.getThirdName() != null
                                ? patient.getThirdName()
                                : "",
                        patient.getLastName() != null
                                ? patient.getLastName()
                                : ""
                ).trim();

                mrn = patient.getMedicalRecordNumber();
            }

            // =====================================================
            // VIEW MARKER / NORMAL RANGE
            // =====================================================

            TestResultMarker viewMarker = result.getMarker();
            String viewNormalRange = result.getNormalRangeValue();

            TestResultType resultType = null;

            try {

                resultType =
                        diagnosticTestProfileClient
                                .getResultTypeByProfileTestIdInternal(
                                        result.getProfileTestId()
                                );

            } catch (Exception ignored) {

                LOG.warn(
                        "ResultType fetch failed for Results page. profileTestId={}",
                        result.getProfileTestId()
                );
            }

            if (patient != null && resultType != null) {

                NormalRangeMatchDTO best =
                        normalRangeMatcherService.findBestNormalRange(
                                result.getProfileTestId(),
                                patient.getId()
                        );

                viewMarker =
                        NormalRangeMatcherService.calculateMarker(
                                resultType,
                                result.getResultValueNumber(),
                                result.getResultValueText(),
                                best
                        );

                viewNormalRange =
                        buildViewNormalRange(best);
            }

            // =====================================================
            // NOTE
            // =====================================================

            boolean hasNote =
                    diagnosticOrderTestResultTechnicianNoteRepository
                            .existsByResultId(result.getId());

            // =====================================================
            // RESULTS PAGE VM
            // =====================================================

            return new DiagnosticOrderTestResultResultsVM(

                    result.getId(),
                    result.getOrderTestId(),

                    orderTest.getTestId(),
                    result.getProfileTestId(),

                    result.getResultValueNumber(),
                    result.getResultValueText(),

                    result.getMarker(),
                    viewMarker,
                    viewNormalRange,

                    result.getCreatedDate(),

                    patientName,
                    mrn,

                    order != null
                            ? order.getCreatedBy()
                            : result.getCreatedBy(),

                    order != null
                            ? order.getCreatedDate()
                            : result.getCreatedDate(),

                    order != null
                            ? order.getEncounterId()
                            : null,

                    hasNote,

                    isRadiologyTest(orderTest)
            );
        });
    }

    private Long resolvePatientId(Long orderTestId) {

        return orderTestRepository.findById(orderTestId)
                .map(DiagnosticOrderTest::getOrderId)
                .flatMap(orderRepository::findById)
                .map(DiagnosticOrder::getPatientId)
                .orElse(null);
    }

    private String buildViewNormalRange(NormalRangeMatchDTO bestNormalRangeMatch) {
        if (bestNormalRangeMatch == null) {
            return " ";
        }

        if (bestNormalRangeMatch.resultText() != null && !bestNormalRangeMatch.resultText().isBlank()) {
            return bestNormalRangeMatch.resultText();
        }


        if (bestNormalRangeMatch.lovKeys() != null && !bestNormalRangeMatch.lovKeys().isEmpty()) {
            return String.join(", ", bestNormalRangeMatch.lovKeys());
        }

        Double from = bestNormalRangeMatch.rangeFrom();
        Double to = bestNormalRangeMatch.rangeTo();

        NormalRangeType normalRangeType = bestNormalRangeMatch.normalRangeType();
        if (normalRangeType == null) {
            normalRangeType = NormalRangeType.RANGE;
        }

        return switch (normalRangeType) {
            case RANGE -> {
                if (from != null && to != null) {
                    yield from + " - " + to;
                }
                if (from != null) {
                    yield ">= " + from;
                }
                if (to != null) {
                    yield "<= " + to;
                }
                yield " ";
            }
            case LESS_THAN -> to != null ? "< " + to : " ";
            case MORE_THAN -> from != null ? "> " + from : " ";
        };
    }

    @Transactional(readOnly = true)
    public List<Long> findFilledProfileTestIds(
            List<Long> orderTestIds
    ) {

        return diagnosticOrderTestResultRepository.findByOrderTestIdIn(orderTestIds)
                .stream()
                .map(DiagnosticOrderTestResult::getProfileTestId)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Long>> findFilledProfileTestIdsByOrderTest(
            List<Long> orderTestIds
    ) {

        return diagnosticOrderTestResultRepository.findByOrderTestIdIn(orderTestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        DiagnosticOrderTestResult::getOrderTestId,
                        Collectors.mapping(
                                DiagnosticOrderTestResult::getProfileTestId,
                                Collectors.toList()
                        )
                ));
    }

    @Transactional(readOnly = true)
    public List<LabResultLog> findLabResultLogsByResultId(Long resultId) {
        return labResultLogRepository.findAllByResultIdOrderByResultDateDesc(resultId);
    }

    private static final Set<TestResultMarker> ABNORMAL_RESULT_MARKERS = Set.of(
            TestResultMarker.UPPER_LIMIT,
            TestResultMarker.LOWER_LIMIT,
            TestResultMarker.ABNORMAL_MARKER,
            TestResultMarker.CRITICAL_UPPER,
            TestResultMarker.CRITICAL_LOWER
    );
    private static final Set<TestResultMarker> CRITICAL_RESULT_MARKERS = Set.of(
            TestResultMarker.CRITICAL_UPPER,
            TestResultMarker.CRITICAL_LOWER
    );

    private void notificationForResultReady(DiagnosticOrderTestResult result, TestResultMarker calculatedMarker) {
        if (result == null || result.getOrderTestId() == null) {
            return;
        }

        DiagnosticOrderTest orderTest = diagnosticOrderTestRepository.findById(result.getOrderTestId())
                .orElse(null);

        if (orderTest == null || orderTest.getOrderId() == null) {
            LOG.warn(
                    "Skip diagnostic result notification because order test/order is missing. resultId={}, orderTestId={}",
                    result.getId(),
                    result.getOrderTestId()
            );
            return;
        }

        DiagnosticOrder order = diagnosticOrderRepository.findById(orderTest.getOrderId())
                .orElse(null);

        if (order == null) {
            LOG.warn(
                    "Skip diagnostic result notification because order is missing. resultId={}, orderId={}",
                    result.getId(),
                    orderTest.getOrderId()
            );
            return;
        }
        DiagnosticOrderTestReport report = null;

        if (isRadiologyTest(orderTest)) {
            report = diagnosticOrderTestReportRepository
                    .findFirstByOrderTestIdOrderByIdDesc(orderTest.getId())
                    .orElse(null);
        }

        NotificationCode notificationCode = resolveResultReadyNotificationCode(
                orderTest,
                calculatedMarker,
                report
        );

        if (notificationCode == null) {
            return;
        }

        Long departmentId = resolveDepartmentId(order, orderTest);
        DepartmentDTO department = departmentHelper.getDepartment(departmentId);

        if (departmentId == null) {
            LOG.warn(
                    "Skip diagnostic result notification because department is missing. resultId={}, orderTestId={}, orderId={}",
                    result.getId(),
                    orderTest.getId(),
                    order.getId()
            );
            return;
        }
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = notificationHelper.resolveRecipients(departmentId, login, result.getCreatedBy(), resolvePatient(order.getPatientId()).orElse(null), null, false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result_id", result.getId());
        data.put("order_test_id", orderTest.getId());
        data.put("order_id", order.getId());
        data.put("patient_id", order.getPatientId());
        data.put("patient_name", notificationHelper.getPatientName(resolvePatient(order.getPatientId()).orElse(null)));
        data.put("encounter_id", order.getEncounter() != null ? order.getEncounter().getId() : "");
        data.put("department_id", departmentId);
        data.put("department_name", department.name());
        data.put("test_id", orderTest.getTestId());
        data.put("test_type", orderTest.getOrderType() != null ? orderTest.getOrderType().toString() : "");
        data.put("marker", calculatedMarker != null ? calculatedMarker.toString() : "");
        data.put("normal_range", result.getNormalRangeValue() != null ? result.getNormalRangeValue() : "");
        data.put("result_value_number", result.getResultValueNumber() != null ? result.getResultValueNumber() : "");
        data.put("result_value_text", result.getResultValueText() != null ? result.getResultValueText() : "");
        data.put("report_id", report != null ? report.getId() : "");
        data.put("report_severity", report != null && report.getSeverity() != null ? report.getSeverity().toString() : "");
        try {
            LOG.debug(
                    "Creating diagnostic result ready in-app notification. resultId={}, code={}, departmentId={}, marker={}, resultLevel={}, recipientsByRule={}",
                    result.getId(),
                    notificationCode,
                    departmentId,
                    calculatedMarker,
                    report != null && report.getSeverity() != null ? report.getSeverity() : "",
                    recipientsByRule
            );

            notificationHelper.sendNotification(null,
                    notificationCode,
                    recipientsByRule,
                    data,
                    "DIAGNOSTIC_ORDER_TEST_RESULT",
                    result.getId());
        } catch (Exception e) {
            LOG.warn(
                    "Failed to create diagnostic result ready notification. resultId={}, code={}, error={}",
                    result.getId(),
                    notificationCode,
                    e.getMessage()
            );
        }
    }

    private void notificationForCriticalLaboratoryResultReady(DiagnosticOrderTestResult result, TestResultMarker calculatedMarker) {
        if (result == null || result.getOrderTestId() == null) {
            return;
        }

        DiagnosticOrderTest orderTest = diagnosticOrderTestRepository.findById(result.getOrderTestId())
                .orElse(null);

        if (orderTest == null || orderTest.getOrderId() == null) {
            LOG.warn(
                    "Skip diagnostic result notification because order test/order is missing. resultId={}, orderTestId={}",
                    result.getId(),
                    result.getOrderTestId()
            );
            return;
        }

        DiagnosticOrder order = diagnosticOrderRepository.findById(orderTest.getOrderId())
                .orElse(null);

        if (order == null) {
            LOG.warn(
                    "Skip diagnostic result notification because order is missing. resultId={}, orderId={}",
                    result.getId(),
                    orderTest.getOrderId()
            );
            return;
        }

        if (!isLaboratoryTest(orderTest)) {
            LOG.warn(
                    "Skip diagnostic result notification because it is not laboratory. resultId={}, orderId={}",
                    result.getId(),
                    orderTest.getOrderId()
            );
            return;
        }
        if (!isCriticalMarker(calculatedMarker)) {
            LOG.debug(
                    "Skip diagnostic result notification because it is not critical marker. resultId={}, orderId={}, marker={}",
                    result.getId(),
                    orderTest.getOrderId(),
                    calculatedMarker
            );
            return;
        }


        Long departmentId = resolveDepartmentId(order, orderTest);
        DepartmentDTO department = departmentHelper.getDepartment(departmentId);

        if (departmentId == null) {
            LOG.warn(
                    "Skip diagnostic result notification because department is missing. resultId={}, orderTestId={}, orderId={}",
                    result.getId(),
                    orderTest.getId(),
                    order.getId()
            );
            return;
        }
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = notificationHelper.resolveRecipients(departmentId, login, result.getCreatedBy(), resolvePatient(order.getPatientId()).orElse(null), null, false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result_id", result.getId());
        data.put("order_test_id", orderTest.getId());
        data.put("order_id", order.getId());
        data.put("patient_id", order.getPatientId());
        data.put("patient_name", notificationHelper.getPatientName(resolvePatient(order.getPatientId()).orElse(null)));
        data.put("encounter_id", order.getEncounter() != null ? order.getEncounter().getId() : "");
        data.put("department_id", departmentId);
        data.put("department_name", department.name());
        data.put("test_id", orderTest.getTestId());
        data.put("test_type", orderTest.getOrderType() != null ? orderTest.getOrderType().toString() : "");
        data.put("marker", calculatedMarker != null ? calculatedMarker.toString() : "");
        data.put("normal_range", result.getNormalRangeValue() != null ? result.getNormalRangeValue() : "");
        data.put("result_value_number", result.getResultValueNumber() != null ? result.getResultValueNumber() : "");
        data.put("result_value_text", result.getResultValueText() != null ? result.getResultValueText() : "");
        try {
            LOG.debug(
                    "Creating diagnostic result ready notification. resultId={}, code={}, departmentId={}, marker={},  recipientsByRule={}",
                    result.getId(),
                    NotificationCode.CRITICAL_LABORATORY_RESULT,
                    departmentId,
                    calculatedMarker,
                    recipientsByRule
            );

            notificationHelper.sendNotification(null,
                    NotificationCode.CRITICAL_LABORATORY_RESULT,
                    recipientsByRule,
                    data,
                    "DIAGNOSTIC_ORDER_TEST_RESULT",
                    result.getId());
        } catch (Exception e) {
            LOG.warn(
                    "Failed to create diagnostic result ready notification. resultId={}, code={}, error={}",
                    result.getId(),
                    NotificationCode.CRITICAL_LABORATORY_RESULT,
                    e.getMessage()
            );
        }
    }

    private NotificationCode resolveResultReadyNotificationCode(DiagnosticOrderTest orderTest, TestResultMarker calculatedMarker, DiagnosticOrderTestReport report) {
        if (isLaboratoryTest(orderTest) && isAbnormalMarker(calculatedMarker)) {
            return NotificationCode.DIAGNOSTIC_LAB_ABNORMAL_RESULT_READY;
        }

        if (isRadiologyTest(orderTest) && isSevereRadiologyReport(report)) {
            return NotificationCode.RADIOLOGY_SEVERE_RESULT_READY;
        }

        return null;
    }

    private boolean isLaboratoryTest(DiagnosticOrderTest orderTest) {
        if (orderTest == null || orderTest.getOrderType() == null) {
            return false;
        }

        return orderTest.getOrderType() == TestType.LABORATORY;
    }

    private boolean isRadiologyTest(DiagnosticOrderTest orderTest) {
        return orderTest != null && orderTest.getOrderType() == TestType.RADIOLOGY;
    }

    private boolean isAbnormalMarker(TestResultMarker marker) {
        return marker != null && ABNORMAL_RESULT_MARKERS.contains(marker);
    }

    private boolean isCriticalMarker(TestResultMarker marker) {
        return marker != null && CRITICAL_RESULT_MARKERS.contains(marker);
    }

    private Long resolveDepartmentId(DiagnosticOrder order, DiagnosticOrderTest orderTest) {
        if (orderTest != null && orderTest.getReceivedDepartmentId() != null) {
            return orderTest.getReceivedDepartmentId();
        }

        if (order != null && order.getFromDepartmentId() != null) {
            return order.getFromDepartmentId();
        }

        if (order != null && order.getEncounter() != null) {
            return order.getEncounter().getDepartmentId();
        }

        return null;
    }

    private Optional<Patient> resolvePatient(Long patientId) {
        if (patientId == null) {
            return null;
        }

        return patientRepository.findById(patientId);
    }

    private boolean isSevereRadiologyReport(DiagnosticOrderTestReport report) {
        if (report == null || report.getSeverity() == null) {
            return false;
        }
        return Severity.SEVERE == report.getSeverity() || Severity.CRITICAL == report.getSeverity();
    }

    public Specification<DiagnosticOrderTestResult> buildResultSpecification(
            List<Long> orderIdInFilter,
            Long orderTestIdFilter,
            Long profileTestIdFilter,
            List<TestResultMarker> markerInFilter,
            List<TestResultMarker> excludeMarkerInFilter,
            DiagnosticStatus processingStatusFilter,
            String approvedByFilter,
            String rejectedByFilter,
            String reviewByFilter,
            Instant approvedDateFromFilter,
            Instant approvedDateToFilter,
            Instant rejectedDateFromFilter,
            Instant rejectedDateToFilter,
            Instant reviewDateFromFilter,
            Instant reviewDateToFilter,
            Boolean reviewed,
            List<Long> fromDepartmentIn,
            String patientName,
            String mrn,
            List<Long> patientIdIn,
            String orderNumber
    ) {

        return (testResultRoot, criteriaQuery, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            boolean needOrderIdFilter =
                    orderIdInFilter != null && !orderIdInFilter.isEmpty();

            boolean needOrderFilter =
                    fromDepartmentIn != null && !fromDepartmentIn.isEmpty();

            boolean needOrderNumberFilter =
                    orderNumber != null;

            boolean needPatientIdFilter =
                    patientIdIn != null && !patientIdIn.isEmpty();

            boolean needPatientFilter =
                    (patientName != null && !patientName.isBlank()) ||
                            (mrn != null && !mrn.isBlank());

            boolean needSubquery =
                    needOrderIdFilter ||
                            needOrderFilter ||
                            needOrderNumberFilter ||
                            needPatientIdFilter ||
                            needPatientFilter;

            if (needSubquery) {

                var subQuery = criteriaQuery.subquery(Long.class);

                var testRoot = subQuery.from(DiagnosticOrderTest.class);
                var orderRoot = subQuery.from(DiagnosticOrder.class);

                List<Predicate> subPredicates = new ArrayList<>();

                subPredicates.add(criteriaBuilder.equal(
                        testRoot.get("id"),
                        testResultRoot.get("orderTestId")
                ));

                subPredicates.add(criteriaBuilder.equal(
                        orderRoot.get("id"),
                        testRoot.get("orderId")
                ));

                if (needOrderIdFilter) {
                    subPredicates.add(orderRoot.get("id").in(orderIdInFilter));
                }

                if (needOrderFilter) {
                    subPredicates.add(
                            orderRoot.get("fromDepartmentId")
                                    .in(fromDepartmentIn)
                    );
                }

                if (needOrderNumberFilter &&
                        orderNumber != null &&
                        !orderNumber.isBlank()) {

                    subPredicates.add(
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(
                                            orderRoot.get("orderNumber")
                                    ),
                                    "%" + orderNumber
                                            .trim()
                                            .toLowerCase() + "%"
                            )
                    );
                }

                if (needPatientIdFilter) {
                    subPredicates.add(
                            orderRoot.get("patientId")
                                    .in(patientIdIn)
                    );
                }

                if (needPatientFilter) {

                    var patientRoot = subQuery.from(Patient.class);

                    subPredicates.add(criteriaBuilder.equal(
                            patientRoot.get("id"),
                            orderRoot.get("patientId")
                    ));

                    if (mrn != null && !mrn.isBlank()) {

                        subPredicates.add(
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(
                                                patientRoot.get(
                                                        "medicalRecordNumber"
                                                )
                                        ),
                                        "%" + mrn.trim().toLowerCase() + "%"
                                )
                        );
                    }

                    if (patientName != null &&
                            !patientName.isBlank()) {

                        String like =
                                "%" +
                                        patientName.trim().toLowerCase() +
                                        "%";

                        subPredicates.add(
                                criteriaBuilder.or(
                                        criteriaBuilder.like(
                                                criteriaBuilder.lower(
                                                        patientRoot.get("firstName")
                                                ),
                                                like
                                        ),
                                        criteriaBuilder.like(
                                                criteriaBuilder.lower(
                                                        patientRoot.get("secondName")
                                                ),
                                                like
                                        ),
                                        criteriaBuilder.like(
                                                criteriaBuilder.lower(
                                                        patientRoot.get("thirdName")
                                                ),
                                                like
                                        ),
                                        criteriaBuilder.like(
                                                criteriaBuilder.lower(
                                                        patientRoot.get("lastName")
                                                ),
                                                like
                                        )
                                )
                        );
                    }
                }

                subQuery.select(testRoot.get("id"))
                        .where(subPredicates.toArray(new Predicate[0]));

                predicates.add(criteriaBuilder.exists(subQuery));
            }

            if (orderTestIdFilter != null) {
                predicates.add(criteriaBuilder.equal(
                        testResultRoot.get("orderTestId"),
                        orderTestIdFilter
                ));
            }

            if (profileTestIdFilter != null) {
                predicates.add(criteriaBuilder.equal(
                        testResultRoot.get("profileTestId"),
                        profileTestIdFilter
                ));
            }

            if (processingStatusFilter != null) {
                predicates.add(criteriaBuilder.equal(
                        testResultRoot.get("processingStatus"),
                        processingStatusFilter
                ));
            }

            if (markerInFilter != null &&
                    !markerInFilter.isEmpty()) {

                predicates.add(
                        testResultRoot.get("marker")
                                .in(markerInFilter)
                );
            }

            if (excludeMarkerInFilter != null &&
                    !excludeMarkerInFilter.isEmpty()) {

                predicates.add(
                        criteriaBuilder.not(
                                testResultRoot.get("marker")
                                        .in(excludeMarkerInFilter)
                        )
                );
            }

            if (approvedByFilter != null &&
                    !approvedByFilter.isBlank()) {

                predicates.add(criteriaBuilder.equal(
                        testResultRoot.get("approvedBy"),
                        approvedByFilter
                ));
            }

            if (rejectedByFilter != null &&
                    !rejectedByFilter.isBlank()) {

                predicates.add(criteriaBuilder.equal(
                        testResultRoot.get("rejectedBy"),
                        rejectedByFilter
                ));
            }

            if (reviewByFilter != null &&
                    !reviewByFilter.isBlank()) {

                predicates.add(criteriaBuilder.equal(
                        testResultRoot.get("reviewBy"),
                        reviewByFilter
                ));
            }

            if (approvedDateFromFilter != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                testResultRoot.get("approvedDate"),
                                approvedDateFromFilter
                        )
                );
            }

            if (approvedDateToFilter != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                testResultRoot.get("approvedDate"),
                                approvedDateToFilter
                        )
                );
            }

            if (rejectedDateFromFilter != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                testResultRoot.get("rejectedDate"),
                                rejectedDateFromFilter
                        )
                );
            }

            if (rejectedDateToFilter != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                testResultRoot.get("rejectedDate"),
                                rejectedDateToFilter
                        )
                );
            }

            if (reviewDateFromFilter != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                testResultRoot.get("reviewDate"),
                                reviewDateFromFilter
                        )
                );
            }

            if (reviewDateToFilter != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                testResultRoot.get("reviewDate"),
                                reviewDateToFilter
                        )
                );
            }

            if (reviewed != null) {

                predicates.add(
                        reviewed
                                ? criteriaBuilder.isNotNull(
                                testResultRoot.get("reviewDate")
                        )
                                : criteriaBuilder.isNull(
                                testResultRoot.get("reviewDate")
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };
    }
}