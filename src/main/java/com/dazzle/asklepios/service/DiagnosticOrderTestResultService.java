package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.SetupServiceClient;
import com.dazzle.asklepios.client.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestResultType;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
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

    private final DiagnosticOrderTestResultRepository repository;
    private final DiagnosticOrderTestResultStatusService statusService;
    private final DiagnosticOrderTestRepository orderTestRepository;
    private final DiagnosticOrderRepository orderRepository;
    private final SetupServiceClient setupServiceClient;
    private final NormalRangeMatcherService normalRangeMatcherService;

    public DiagnosticOrderTestResultService(
            DiagnosticOrderTestResultRepository repository,
            DiagnosticOrderTestResultStatusService statusService,
            DiagnosticOrderTestRepository orderTestRepository,
            DiagnosticOrderRepository orderRepository,
            SetupServiceClient setupServiceClient,
            NormalRangeMatcherService normalRangeMatcherService
    ) {
        this.repository = repository;
        this.statusService = statusService;
        this.orderTestRepository = orderTestRepository;
        this.orderRepository = orderRepository;
        this.setupServiceClient = setupServiceClient;
        this.normalRangeMatcherService = normalRangeMatcherService;
    }

    // =========================================================
    // CREATE
    // =========================================================

    public DiagnosticOrderTestResult create(DiagnosticOrderTestResultCreateDTO dto) {

        DiagnosticOrderTestResult r = new DiagnosticOrderTestResult();

        r.setOrderTestId(dto.orderTestId());
        r.setProfileTestId(dto.profileTestId());
        r.setResultValueNumber(dto.resultValueNumber());
        r.setResultValueText(dto.resultValueText());
        r.setMarker(dto.marker());
        r.setNormalRangeValue(dto.normalRangeValue());
        r.setProcessingStatus(DiagnosticStatus.RESULT_READY);

        DiagnosticOrderTestResult saved = repository.save(r);

        statusService.recomputeTestProcessingStatusFromResults(
                saved.getOrderTestId()
        );

        return saved;
    }

    // =========================================================
    // UPDATE WITH VALIDATION
    // =========================================================

    public DiagnosticOrderTestResult updateWithValidation(
            Long id,
            DiagnosticOrderTestResultUpdateDTO dto
    ) {

        DiagnosticOrderTestResult existing = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));

        existing.setOrderTestId(dto.orderTestId());
        existing.setProfileTestId(dto.profileTestId());
        existing.setResultValueNumber(dto.resultValueNumber());
        existing.setResultValueText(dto.resultValueText());
        existing.setMarker(dto.marker());
        existing.setNormalRangeValue(dto.normalRangeValue());

        return repository.save(existing);
    }

    // =========================================================
    // APPROVE WITH FULL BUSINESS LOGIC
    // =========================================================

    public DiagnosticOrderTestResult approveWithBusinessLogic(
            Long id,
            String username
    ) {

        DiagnosticOrderTestResult result = repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));

        Long patientId = resolvePatientId(result.getOrderTestId());

        TestResultMarker viewMarker = result.getMarker();
        String viewNormalRange = result.getNormalRangeValue();

        TestResultType resultType;

        try {
            resultType = setupServiceClient
                    .getResultTypeByProfileTestIdInternal(
                            result.getProfileTestId()
                    );
        } catch (Exception e) {
            throw new BadRequestAlertException(
                    "setup_service_error",
                    "diagnostic_order_tests_result",
                    "Failed to fetch result type for profileTestId "
                            + result.getProfileTestId()
            );
        }

        if (patientId != null) {

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

        return statusService.approve(
                id,
                username,
                viewMarker,
                viewNormalRange
        );
    }

    // =========================================================
    // FILTER WITH VIEW
    // =========================================================

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTestResultResponseVM> filterWithView(
            Specification<DiagnosticOrderTestResult> spec,
            Pageable pageable
    ) {

        Page<DiagnosticOrderTestResult> page =
                repository.findAll(spec, pageable);

        return page.map(result -> {

            Long patientId =
                    resolvePatientId(result.getOrderTestId());

            TestResultMarker viewMarker = TestResultMarker.UNKNOWN;
            String viewNormalRange = "UNKNOWN";

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

            return DiagnosticOrderTestResultResponseVM
                    .ofEntityWithView(result, viewMarker, viewNormalRange);
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

    private String buildViewNormalRange(NormalRangeMatchDTO best) {

        if (best == null) return "UNKNOWN";

        if (best.resultText() != null && !best.resultText().isBlank())
            return best.resultText();

        if (best.resultLov() != null && !best.resultLov().isBlank())
            return best.resultLov();

        Double from = best.rangeFrom();
        Double to = best.rangeTo();

        if (from != null && to != null)
            return from + " - " + to;

        if (from != null)
            return ">= " + from;

        if (to != null)
            return "<= " + to;

        return "UNKNOWN";
    }

    // =========================================================
    // INTERNAL ENDPOINT HELPERS
    // =========================================================

    @Transactional(readOnly = true)
    public List<Long> findFilledProfileTestIds(
            List<Long> orderTestIds
    ) {

        return repository.findByOrderTestIdIn(orderTestIds)
                .stream()
                .map(DiagnosticOrderTestResult::getProfileTestId)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Long>> findFilledProfileTestIdsByOrderTest(
            List<Long> orderTestIds
    ) {

        return repository.findByOrderTestIdIn(orderTestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        DiagnosticOrderTestResult::getOrderTestId,
                        Collectors.mapping(
                                DiagnosticOrderTestResult::getProfileTestId,
                                Collectors.toList()
                        )
                ));
    }
}
