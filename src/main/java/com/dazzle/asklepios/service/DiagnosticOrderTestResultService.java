package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.SetupServiceClient;
import com.dazzle.asklepios.client.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.LabResultLog;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultTechnicianNoteRepository;
import com.dazzle.asklepios.repository.LabResultLogRepository;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.ApproveResultDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultCreateDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.DiagnosticOrderTestResultUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class DiagnosticOrderTestResultService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticOrderTestResultService.class);

    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;
    private final DiagnosticOrderTestResultStatusService orderTestResultStatusService;
    private final DiagnosticOrderTestRepository orderTestRepository;
    private final DiagnosticOrderRepository orderRepository;
    private final LabResultLogRepository labResultLogRepository;
    private final SetupServiceClient setupServiceClient;
    private final NormalRangeMatcherService normalRangeMatcherService;
    private final DiagnosticOrderTestResultTechnicianNoteRepository diagnosticOrderTestResultTechnicianNoteRepository;

    public DiagnosticOrderTestResultService(
            DiagnosticOrderTestResultRepository repository,
            DiagnosticOrderTestResultStatusService statusService,
            DiagnosticOrderTestRepository orderTestRepository,
            DiagnosticOrderRepository orderRepository,
            LabResultLogRepository labResultLogRepository,
            SetupServiceClient setupServiceClient,
            NormalRangeMatcherService normalRangeMatcherService, DiagnosticOrderTestResultTechnicianNoteRepository diagnosticOrderTestResultTechnicianNoteRepository
    ) {
        this.diagnosticOrderTestResultRepository = repository;
        this.orderTestResultStatusService = statusService;
        this.orderTestRepository = orderTestRepository;
        this.orderRepository = orderRepository;
        this.labResultLogRepository = labResultLogRepository;
        this.setupServiceClient = setupServiceClient;
        this.normalRangeMatcherService = normalRangeMatcherService;

        this.diagnosticOrderTestResultTechnicianNoteRepository = diagnosticOrderTestResultTechnicianNoteRepository;
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


    // =========================================================
    // UPDATE WITH VALIDATION
    // =========================================================

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

        testResult.setOrderTestId(testResultUpdateDTO.orderTestId());
        testResult.setProfileTestId(testResultUpdateDTO.profileTestId());
        testResult.setResultValueNumber(testResultUpdateDTO.resultValueNumber());
        testResult.setResultValueText(testResultUpdateDTO.resultValueText());
        testResult.setMarker(testResultUpdateDTO.marker());
        testResult.setNormalRangeValue(testResultUpdateDTO.normalRangeValue());

        return diagnosticOrderTestResultRepository.save(testResult);
    }

    // =========================================================
    // APPROVE WITH FULL BUSINESS LOGIC
    // =========================================================

    public DiagnosticOrderTestResult approveResult(Long resultId, String approvedBy) {

        DiagnosticOrderTestResult result = diagnosticOrderTestResultRepository.findById(resultId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + resultId
                ));

        Long patientId = resolvePatientId(result.getOrderTestId());

        TestResultMarker viewMarker = result.getMarker();
        String viewNormalRange = result.getNormalRangeValue();

        TestResultType resultType;
        try {
            resultType = setupServiceClient
                    .getResultTypeByProfileTestIdInternal(result.getProfileTestId());
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

        return orderTestResultStatusService.approve(
                new ApproveResultDTO(
                        resultId,
                        approvedBy,
                        viewMarker,
                        viewNormalRange
                )
        );
    }

    // =========================================================
    // FILTER WITH VIEW
    // =========================================================

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
                resultType = setupServiceClient
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

    // =========================================================
    // INTERNAL UTILITIES
    // =========================================================

    private Long resolvePatientId(Long orderTestId) {

        return orderTestRepository.findById(orderTestId)
                .map(DiagnosticOrderTest::getOrderId)
                .flatMap(orderRepository::findById)
                .map(DiagnosticOrder::getPatientId)
                .orElse(null);
    }

    private String buildViewNormalRange(NormalRangeMatchDTO bestNormalRangeMatch) {

        if (bestNormalRangeMatch == null) return " ";

        if (bestNormalRangeMatch.resultText() != null && !bestNormalRangeMatch.resultText().isBlank())
            return bestNormalRangeMatch.resultText();

        if (bestNormalRangeMatch.resultLov() != null && !bestNormalRangeMatch.resultLov().isBlank())
            return bestNormalRangeMatch.resultLov();


        if (bestNormalRangeMatch.lovKeys() != null && !bestNormalRangeMatch.lovKeys().isEmpty()) {
            return String.join(", ", bestNormalRangeMatch.lovKeys());

        }

        Double from = bestNormalRangeMatch.rangeFrom();
        Double to = bestNormalRangeMatch.rangeTo();

        if (from != null && to != null)
            return from + " - " + to;

        if (from != null)
            return ">= " + from;

        if (to != null)
            return "<= " + to;

        return " ";
    }

    // =========================================================
    // INTERNAL ENDPOINT HELPERS
    // =========================================================

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
}
