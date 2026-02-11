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
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service responsible for create/update/delete operations for {@link DiagnosticOrderTestResult}.
 *
 * <p>This service handles result data persistence and enforces basic invariants:
 * <ul>
 *   <li>New results are created with {@link DiagnosticStatus#RESULT_READY} as processing status.</li>
 *   <li>Status transitions (approve/reject/review) are not handled here; use
 *       {@link DiagnosticOrderTestResultStatusService} instead.</li>
 * </ul>
 * </p>
 */
@Service
@Transactional
public class DiagnosticOrderTestResultService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestResultService.class);

    /** Repository for persistence operations. */
    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;

    /** Service that recomputes parent test/order state from results. */
    private final DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService;

    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    private final SetupServiceClient setupServiceClient;
    private final NormalRangeMatcherService normalRangeMatcherService;

    public DiagnosticOrderTestResultService(
            DiagnosticOrderTestResultRepository repository,
            DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService,
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            SetupServiceClient setupServiceClient,
            NormalRangeMatcherService normalRangeMatcherService
    ) {
        this.diagnosticOrderTestResultRepository = repository;
        this.diagnosticOrderTestResultStatusService = diagnosticOrderTestResultStatusService;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.setupServiceClient = setupServiceClient;
        this.normalRangeMatcherService = normalRangeMatcherService;
    }

    public DiagnosticOrderTestResult create(DiagnosticOrderTestResultCreateDTO dto) {
        LOG.debug("[DiagnosticOrderTestResultService] CREATE - start. payload={}", dto);

        DiagnosticOrderTestResult diagnosticOrderTestResult = new DiagnosticOrderTestResult();
        diagnosticOrderTestResult.setOrderTestId(dto.orderTestId());
        diagnosticOrderTestResult.setProfileTestId(dto.profileTestId());

        diagnosticOrderTestResult.setResultValueNumber(dto.resultValueNumber());
        diagnosticOrderTestResult.setResultValueText(dto.resultValueText());
        diagnosticOrderTestResult.setMarker(dto.marker());
        diagnosticOrderTestResult.setNormalRangeValue(dto.normalRangeValue());

        diagnosticOrderTestResult.setProcessingStatus(DiagnosticStatus.RESULT_READY);

        DiagnosticOrderTestResult saved = diagnosticOrderTestResultRepository.save(diagnosticOrderTestResult);

        diagnosticOrderTestResultStatusService.recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        LOG.debug("[DiagnosticOrderTestResultService] CREATE - done. id={} orderTestId={} processingStatus={}",
                saved.getId(), saved.getOrderTestId(), saved.getProcessingStatus());

        return saved;
    }

    public DiagnosticOrderTestResult update(DiagnosticOrderTestResult existing, DiagnosticOrderTestResultUpdateDTO dto) {
        LOG.debug("[DiagnosticOrderTestResultService] UPDATE - start. id={} payload={}", existing.getId(), dto);

        existing.setOrderTestId(dto.orderTestId());
        existing.setProfileTestId(dto.profileTestId());

        existing.setResultValueNumber(dto.resultValueNumber());
        existing.setResultValueText(dto.resultValueText());
        existing.setMarker(dto.marker());
        existing.setNormalRangeValue(dto.normalRangeValue());

        DiagnosticOrderTestResult saved = diagnosticOrderTestResultRepository.save(existing);

        LOG.debug("[DiagnosticOrderTestResultService] UPDATE - done. id={} processingStatus={}",
                saved.getId(), saved.getProcessingStatus());

        return saved;
    }

    public DiagnosticOrderTestResult updateById(Long resultId, DiagnosticOrderTestResultUpdateDTO requestDto) {
        LOG.debug("[DiagnosticOrderTestResultService] UPDATE_BY_ID - start. id={} payload={}", resultId, requestDto);
        DiagnosticOrderTestResult existingResult = getExistingResult(resultId);

        DiagnosticOrderTestResultUpdateDTO fixedDto = new DiagnosticOrderTestResultUpdateDTO(
                resultId,
                requestDto.orderTestId(),
                requestDto.profileTestId(),
                requestDto.resultValueNumber(),
                requestDto.resultValueText(),
                requestDto.marker(),
                requestDto.normalRangeValue()
        );

        DiagnosticOrderTestResult updatedResult = update(existingResult, fixedDto);
        LOG.debug("[DiagnosticOrderTestResultService] UPDATE_BY_ID - done. id={}", updatedResult.getId());
        return updatedResult;
    }

    public DiagnosticOrderTestResult approveWithComputedMarker(Long resultId, String username) {
        LOG.debug("[DiagnosticOrderTestResultService] APPROVE - start. resultId={} username={}", resultId, username);

        DiagnosticOrderTestResult existingResult = getExistingResult(resultId);

        // result -> orderTestId -> orderTest -> orderId -> order -> patientId
        Long orderId = diagnosticOrderTestRepository.findById(existingResult.getOrderTestId())
                .map(DiagnosticOrderTest::getOrderId)
                .orElse(null);

        Long patientId = (orderId == null) ? null
                : diagnosticOrderRepository.findById(orderId)
                .map(DiagnosticOrder::getPatientId)
                .orElse(null);

        TestResultMarker viewMarker = existingResult.getMarker();
        String viewNormalRange = existingResult.getNormalRangeValue();

        TestResultType resultType;
        try {
            resultType = setupServiceClient.getResultTypeByProfileTestIdInternal(existingResult.getProfileTestId());
        } catch (Exception e) {
            throw new BadRequestAlertException(
                    "setup_service_error",
                    "diagnostic_order_tests_result",
                    "Failed to fetch result type for profileTestId " + existingResult.getProfileTestId()
            );
        }

        if (patientId != null) {
            NormalRangeMatchDTO best = normalRangeMatcherService.findBestNormalRange(
                    existingResult.getProfileTestId(),
                    patientId
            );
            viewMarker = NormalRangeMatcherService.calculateMarker(
                    resultType,
                    existingResult.getResultValueNumber(),
                    existingResult.getResultValueText(),
                    best
            );
            viewNormalRange = buildViewNormalRange(best);
        }

        DiagnosticOrderTestResult approvedResult = diagnosticOrderTestResultStatusService.approve(
                resultId,
                username,
                viewMarker,
                viewNormalRange
        );

        LOG.debug("[DiagnosticOrderTestResultService] APPROVE - done. id={} approvedBy={}",
                approvedResult.getId(), approvedResult.getApprovedBy());

        return approvedResult;
    }


    @Transactional(readOnly = true)
    public List<DiagnosticOrderTestResultResponseVM> buildViewResponses(List<DiagnosticOrderTestResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .map(this::buildViewResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findFilledProfileTestIds(List<Long> orderTestIds) {
        if (orderTestIds == null || orderTestIds.isEmpty()) {
            return List.of();
        }
        return orderTestIds.stream()
                .filter(Objects::nonNull)
                .flatMap(orderTestId -> diagnosticOrderTestResultRepository.findDistinctProfileTestIdByOrderTestId(orderTestId).stream())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Long>> findFilledProfileTestIdsByOrderTest(List<Long> orderTestIds) {
        if (orderTestIds == null || orderTestIds.isEmpty()) {
            return Map.of();
        }

        return orderTestIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        orderTestId -> orderTestId,
                        orderTestId -> diagnosticOrderTestResultRepository.findDistinctProfileTestIdByOrderTestId(orderTestId)
                ));
    }

    private DiagnosticOrderTestResult getExistingResult(Long resultId) {
        return diagnosticOrderTestResultRepository.findById(resultId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + resultId
                ));
    }

    private DiagnosticOrderTestResultResponseVM buildViewResponse(DiagnosticOrderTestResult result) {

        Long orderId = diagnosticOrderTestRepository.findById(result.getOrderTestId())
                .map(DiagnosticOrderTest::getOrderId)
                .orElse(null);

        Long patientId = (orderId == null) ? null
                : diagnosticOrderRepository.findById(orderId)
                .map(DiagnosticOrder::getPatientId)
                .orElse(null);

        TestResultMarker viewMarker = result.getMarker();
        String viewNormalRange = result.getNormalRangeValue();

        TestResultType resultType;
        try {
            resultType = setupServiceClient.getResultTypeByProfileTestIdInternal(result.getProfileTestId());
        } catch (Exception e) {
            throw new BadRequestAlertException(
                    "setup_service_error",
                    "diagnostic_order_tests_result",
                    "Failed to fetch result type for profileTestId " + result.getProfileTestId()
            );
        }

        if (patientId != null) {
            NormalRangeMatchDTO best = normalRangeMatcherService.findBestNormalRange(
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

        return DiagnosticOrderTestResultResponseVM.ofEntityWithView(result, viewMarker, viewNormalRange);
    }

    public String buildViewNormalRange(NormalRangeMatchDTO best) {
        if (best == null) return null;

        if (best.resultText() != null && !best.resultText().isBlank()) return best.resultText();
        if (best.resultLov() != null && !best.resultLov().isBlank()) return best.resultLov();

        Double from = best.rangeFrom();
        Double to = best.rangeTo();
        if (from != null && to != null) return from + " - " + to;
        if (from != null) return ">= " + from;
        if (to != null) return "<= " + to;

        return null;
    }
    @Transactional(readOnly = true)
    public Page<DiagnosticOrderTestResult> findAll(Specification<DiagnosticOrderTestResult> spec, Pageable pageable) {
        return diagnosticOrderTestResultRepository.findAll(spec, pageable);
    }
    public void delete(Long id) {
        LOG.debug("[DiagnosticOrderTestResultService] DELETE - start. id={}", id);
        diagnosticOrderTestResultRepository.deleteById(id);
        LOG.debug("[DiagnosticOrderTestResultService] DELETE - done. id={}", id);
    }
}
