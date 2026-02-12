package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.SetupServiceClient;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.ApproveResultDTO;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.RejectResultDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service responsible for managing the lifecycle (status transitions) of {@link DiagnosticOrderTestResult}.
 *
 * <p>This service enforces allowed transitions for {@link DiagnosticStatus} (processingStatus) and updates audit fields
 * (approvedBy/date, rejectedBy/date/reason, reviewBy/date).</p>
 *
 * <p>After a successful approve/reject transition, it updates the {@link DiagnosticOrderTestResult} lifecycle only.
 * Parent test and order statuses are recomputed via status services.</p>
 */
@Service
@Transactional
public class DiagnosticOrderTestResultStatusService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticOrderTestResultStatusService.class);

    /** Repository used to load and persist results. */
    private final DiagnosticOrderTestResultRepository resultRepository;

    /** Service used to apply status transitions on the parent test. */
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;

    /** Repository used to load and persist the parent test when recomputing from results. */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    /** Service used to recompute aggregated lab/radiology statuses on the parent order. */
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    /** Matcher used elsewhere; kept as dependency (not used in this snippet). */
    private final NormalRangeMatcherService normalRangeMatcherService;

    /** Setup service client used to fetch expected profile tests for a test. */
    private final SetupServiceClient setupServiceClient;

    public DiagnosticOrderTestResultStatusService(
            DiagnosticOrderTestResultRepository resultRepository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService,
            NormalRangeMatcherService normalRangeMatcherService,
            SetupServiceClient setupServiceClient
    ) {
        this.resultRepository = resultRepository;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
        this.normalRangeMatcherService = normalRangeMatcherService;
        this.setupServiceClient = setupServiceClient;
    }

    /**
     * Approves a result.
     *
     * <p>Allowed transition: {@link DiagnosticStatus#RESULT_READY} -&gt; {@link DiagnosticStatus#RESULT_APPROVED}.</p>
     *
     * @param approveResultDTO approval command containing result id, approver username, computed marker, and normal range
     * @return updated result after applying approval and persisting audit fields
     */
    public DiagnosticOrderTestResult approve(ApproveResultDTO approveResultDTO) {
        DiagnosticOrderTestResult result = getResult(approveResultDTO.resultId());

        LOG.debug("[ResultApprove] start approveResultDTO={} currentStatus={} orderTestId={}",
                approveResultDTO, result.getProcessingStatus(), result.getOrderTestId());

        ensureTransition(result.getProcessingStatus(), DiagnosticStatus.RESULT_APPROVED);

        result.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        result.setApprovedBy(approveResultDTO.approvedBy());
        result.setApprovedDate(Instant.now());
        result.setMarker(approveResultDTO.marker());
        result.setNormalRangeValue(approveResultDTO.normalRange());

        DiagnosticOrderTestResult saved = resultRepository.save(result);

        diagnosticOrderTestStatusService.approve(saved.getOrderTestId());
        recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        return saved;
    }


    /**
     * Rejects a result.
     *
     * <p>Allowed transition:
     * {@link DiagnosticStatus#NEW} or {@link DiagnosticStatus#RESULT_READY}
     * -&gt; {@link DiagnosticStatus#RESULT_REJECTED}.</p>
     *
     * @param rejectResultDTO rejection command containing result id, rejecting username, and rejection reason
     * @return updated result after applying rejection and persisting audit fields
     */
    public DiagnosticOrderTestResult reject(RejectResultDTO rejectResultDTO) {
        DiagnosticOrderTestResult result = getResult(rejectResultDTO.resultId());

        LOG.debug("[ResultReject] start rejectResultDTO={} currentStatus={} orderTestId={}",
                rejectResultDTO, result.getProcessingStatus(), result.getOrderTestId());

        ensureTransition(result.getProcessingStatus(), DiagnosticStatus.RESULT_REJECTED);

        result.setProcessingStatus(DiagnosticStatus.RESULT_REJECTED);
        result.setRejectedBy(rejectResultDTO.rejectedBy());
        result.setRejectedReason(rejectResultDTO.rejectedReason());
        result.setRejectedDate(Instant.now());

        DiagnosticOrderTestResult saved = resultRepository.save(result);

        recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        return saved;
    }


    /**
     * Toggles review state for a result.
     *
     * <p>If the result has no {@code reviewDate}, it sets {@code reviewBy} and {@code reviewDate}.
     * If it is already reviewed, it clears {@code reviewBy} and {@code reviewDate}.</p>
     *
     * @param resultId result id
     * @param username current username
     * @return updated result
     */
    public DiagnosticOrderTestResult toggleReview(Long resultId, String username) {
        DiagnosticOrderTestResult result = getResult(resultId);

        boolean isReviewed = result.getReviewDate() != null;

        LOG.debug("[ResultToggleReview] start resultId={} orderTestId={} isReviewed={} user={}",
                resultId, result.getOrderTestId(), isReviewed, username);

        if (!isReviewed) {
            result.setReviewBy(username);
            result.setReviewDate(Instant.now());
        } else {
            result.setReviewBy(null);
            result.setReviewDate(null);
        }

        DiagnosticOrderTestResult saved = resultRepository.save(result);

        LOG.debug("[ResultToggleReview] done resultId={} orderTestId={} isReviewedNow={}",
                saved.getId(), saved.getOrderTestId(), saved.getReviewDate() != null);

        return saved;
    }

    /**
     * Recomputes and persists the parent {@link DiagnosticOrderTest} processing status based on its results.
     *
     * <p>Rules:
     * <ul>
     *   <li>If not all expected profile tests are filled -&gt; {@link DiagnosticStatus#PARTIALLY}</li>
     *   <li>If all expected profiles are filled:
     *       <ul>
     *         <li>If all result statuses are the same -&gt; that status</li>
     *         <li>Otherwise -&gt; {@link DiagnosticStatus#PARTIALLY}</li>
     *       </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * <p>After updating the test, it recomputes aggregated lab/radiology statuses on the parent order.</p>
     *
     * @param orderTestId diagnostic order test id
     * @return updated parent test (or unchanged test)
     */
    public DiagnosticOrderTest recomputeTestProcessingStatusFromResults(Long orderTestId) {

        DiagnosticOrderTest orderTest = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        List<Long> expectedProfileTestIds =
                setupServiceClient.getTestProfilesIdsByTestId(orderTest.getTestId());

        if (expectedProfileTestIds == null || expectedProfileTestIds.isEmpty()) {
            LOG.debug("[TestRecompute] skip no expected profiles orderTestId={} testId={}",
                    orderTestId, orderTest.getTestId());
            return orderTest;
        }

        List<Long> filledProfileTestIds =
                resultRepository.findDistinctProfileTestIdsByOrderTestId(orderTestId);

        if (filledProfileTestIds == null || filledProfileTestIds.isEmpty()) {
            LOG.debug("[TestRecompute] skip no filled profiles orderTestId={} testId={}",
                    orderTestId, orderTest.getTestId());
            return orderTest;
        }

        boolean areAllProfilesFilled = filledProfileTestIds.containsAll(expectedProfileTestIds);

        LOG.debug("[TestRecompute] inputs orderTestId={} testId={} expectedProfiles={} filledProfiles={} allProfilesFilled={} oldTestStatus={}",
                orderTestId,
                orderTest.getTestId(),
                expectedProfileTestIds.size(),
                filledProfileTestIds.size(),
                areAllProfilesFilled,
                orderTest.getProcessingStatus());

        List<DiagnosticStatus> resultProcessingStatuses = null;
        DiagnosticStatus newProcessingStatus;

        if (!areAllProfilesFilled) {
            newProcessingStatus = DiagnosticStatus.PARTIALLY;
        } else {
            resultProcessingStatuses = resultRepository.findProcessingStatusesByOrderTestId(orderTestId);
            newProcessingStatus = aggregateResultStatuses(resultProcessingStatuses);
        }

        LOG.debug("[TestRecompute] decision orderTestId={} resultStatuses={} newTestStatus={}",
                orderTestId, resultProcessingStatuses, newProcessingStatus);

        if (orderTest.getProcessingStatus() == newProcessingStatus) {
            LOG.debug("[TestRecompute] no change orderTestId={} status={}", orderTestId, newProcessingStatus);
            return orderTest;
        }

        orderTest.setProcessingStatus(newProcessingStatus);
        DiagnosticOrderTest savedOrderTest = diagnosticOrderTestRepository.save(orderTest);

        LOG.info("[TestRecompute] updated orderTestId={} testId={} oldStatus={} newStatus={} orderId={}",
                orderTestId, savedOrderTest.getTestId(), orderTest.getProcessingStatus(), savedOrderTest.getProcessingStatus(), savedOrderTest.getOrderId());

        diagnosticOrderStatusService.recomputeLabRadStatuses(savedOrderTest.getOrderId());

        LOG.debug("[TestRecompute] recompute order statuses done orderId={}", savedOrderTest.getOrderId());

        return savedOrderTest;
    }

    private DiagnosticOrderTestResult getResult(Long id) {
        return resultRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));
    }

    private void ensureTransition(DiagnosticStatus fromStatus, DiagnosticStatus toStatus) {

        if (toStatus == DiagnosticStatus.RESULT_APPROVED) {
            if (fromStatus != DiagnosticStatus.RESULT_READY ) throw invalid(fromStatus, toStatus);
            return;
        }

        if (toStatus == DiagnosticStatus.RESULT_REJECTED) {
            if (fromStatus != DiagnosticStatus.NEW && fromStatus != DiagnosticStatus.RESULT_READY) {
                throw invalid(fromStatus, toStatus);
            }
            return;
        }

        throw invalid(fromStatus, toStatus);
    }

    private BadRequestAlertException invalid(DiagnosticStatus fromStatus, DiagnosticStatus toStatus) {
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests_result",
                "Invalid transition " + fromStatus + " -> " + toStatus
        );
    }

    /**
     * Aggregates multiple result statuses into a single status for the parent test.
     *
     * <p>Rule: if all statuses are equal -&gt; return that status, otherwise -&gt; PARTIALLY.</p>
     */
    private DiagnosticStatus aggregateResultStatuses(List<DiagnosticStatus> resultStatuses) {
        if (resultStatuses == null || resultStatuses.isEmpty()) {
            LOG.debug("[TestAggregate] empty statuses -> NEW");
            return DiagnosticStatus.NEW;
        }

        DiagnosticStatus firstStatus = resultStatuses.get(0);

        boolean areAllSameStatus =
                resultStatuses.stream().allMatch(status -> status == firstStatus);

        DiagnosticStatus aggregated =
                areAllSameStatus ? firstStatus : DiagnosticStatus.PARTIALLY;

        LOG.debug("[TestAggregate] statuses={} first={} allSame={} aggregated={}",
                resultStatuses, firstStatus, areAllSameStatus, aggregated);

        return aggregated;
    }
}
