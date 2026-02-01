package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.SetupServiceClient;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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
     <p>After a successful approve/reject transition, it updates the
     {@link DiagnosticOrderTestResult} lifecycle only.
     Parent test and order statuses are NOT affected here.</p>

 */
@Service
@Transactional
public class DiagnosticOrderTestResultStatusService {

    /** Repository used to load and persist results. */
    private final DiagnosticOrderTestResultRepository resultRepository;

    /** Service used to apply status transitions on the parent test. */
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;

    /** Repository used to load and persist the parent test when recomputing from results. */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    /** Service used to recompute aggregated lab/radiology statuses on the parent order. */
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
  private final  NormalRangeMatcherService normalRangeMatcherService;
  private  final SetupServiceClient setupServiceClient;
    /**
     * Constructs the service with required dependencies.
     *
     * @param resultRepository repository for result persistence
     * @param diagnosticOrderTestStatusService service for updating parent test status
     * @param diagnosticOrderTestRepository repository for reading/writing parent tests
     * @param diagnosticOrderStatusService service for recomputing parent order statuses
     */
    public DiagnosticOrderTestResultStatusService(
            DiagnosticOrderTestResultRepository resultRepository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService, NormalRangeMatcherService normalRangeMatcherService, SetupServiceClient setupServiceClient
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
     * <p>Side effects:
     * <ul>
     *   <li>Sets {@code approvedBy} and {@code approvedDate}.</li>
     *   <li>Transitions the parent test to approved via {@link DiagnosticOrderTestStatusService#approve(Long)}.</li>
     *   <li>Recomputes the parent test processing status from all its results (aggregate).</li>
     * </ul>
     * </p>
     *
     * @param resultId result id
     * @param approvedBy username approving the result
     * @return updated result
     */
    public DiagnosticOrderTestResult approve(Long resultId, String approvedBy, TestResultMarker marker,String normalrange) {
        DiagnosticOrderTestResult r = getResult(resultId);

        DiagnosticStatus from = normalize(r.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.RESULT_APPROVED);

        r.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        r.setApprovedBy(approvedBy);
        r.setApprovedDate(Instant.now());
        r.setMarker(marker);
        r.setNormalRangeValue(normalrange);
        DiagnosticOrderTestResult saved = resultRepository.save(r);

        diagnosticOrderTestStatusService.approve(saved.getOrderTestId());
        recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        return saved;
    }

    /**
     * Rejects a result.
     *
     * <p>Allowed transition:
     * {@link DiagnosticStatus#NEW}, {@link DiagnosticStatus#RESULT_READY}, or {@link DiagnosticStatus#REVIEWED}
     * -&gt; {@link DiagnosticStatus#REJECTED}.</p>
     *
     * <p>Side effects:
     * <ul>
     *   <li>Sets {@code rejectedBy}, {@code rejectedDate}, and {@code rejectedReason}.</li>
     *   <li>Transitions the parent test to rejected via
     *       {@link DiagnosticOrderTestStatusService#reject(Long, String, String)}.</li>
     *   <li>Recomputes the parent test processing status from all its results (aggregate).</li>
     * </ul>
     * </p>
     *
     * @param resultId result id
     * @param rejectedBy username rejecting the result
     * @param rejectedReason rejection reason
     * @return updated result
     */
    public DiagnosticOrderTestResult reject(Long resultId, String rejectedBy, String rejectedReason) {
        DiagnosticOrderTestResult r = getResult(resultId);

        DiagnosticStatus from = normalize(r.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.RESULT_REJECTED);

        r.setProcessingStatus(DiagnosticStatus.RESULT_REJECTED);
        r.setRejectedBy(rejectedBy);
        r.setRejectedReason(rejectedReason);
        r.setRejectedDate(Instant.now());

        DiagnosticOrderTestResult saved = resultRepository.save(r);
        recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        return saved;
    }


    /**
     * Toggles review state for a result.
     *
     * <p>If the result has no {@code reviewDate}, it sets {@code reviewBy} and {@code reviewDate}.
     * If it is already reviewed, it clears {@code reviewBy} and {@code reviewDate}.</p>
     *
     * <p>This method does not change {@code processingStatus}.</p>
     *
     * @param resultId result id
     * @param username current username
     * @return updated result
     */
    public DiagnosticOrderTestResult toggleReview(Long resultId, String username) {
        DiagnosticOrderTestResult r = getResult(resultId);

        boolean reviewed = r.getReviewDate() != null;

        if (!reviewed) {
            r.setReviewBy(username);
            r.setReviewDate(Instant.now());
        } else {
            r.setReviewBy(null);
            r.setReviewDate(null);
        }

        return resultRepository.save(r);
    }

    /**
     * Recomputes and persists the parent {@link DiagnosticOrderTest} processing status based on its results.
     *
     * <p>Aggregation rules (current implementation):
     * <ul>
     *   <li>If any result is {@code REJECTED} -&gt; test becomes {@code REJECTED}.</li>
     *   <li>If all results are {@code RESULT_APPROVED} -&gt; test becomes {@code RESULT_APPROVED}.</li>
     *   <li>If any result is {@code RESULT_READY} -&gt; test becomes {@code RESULT_READY}.</li>
     *   <li>Otherwise -&gt; test becomes {@code NEW}.</li>
     * </ul>
     * </p>
     *
     * <p>After updating the test, it recomputes the aggregated lab/radiology statuses on the parent order.</p>
     *
     * @param orderTestId diagnostic order test id
     * @return updated parent test
     * @throws BadRequestAlertException if the parent test does not exist
     */
    public DiagnosticOrderTest recomputeTestProcessingStatusFromResults(Long orderTestId) {

        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        // profile ids (active lab profiles) from setup-service
        List<Long> profileIds = setupServiceClient.getTestProfilesIdsByTestId(test.getTestId());
        if (profileIds == null || profileIds.isEmpty()) {
            // no profiles configured => keep current status (or NEW)
            return test;
        }

        // profile ids that already have results (excluding CANCELLED results)
        List<Long> filledProfileIds = resultRepository.findDistinctProfileTestIdsByOrderTestId(orderTestId);
        if (filledProfileIds == null) filledProfileIds = List.of();

        boolean hasAnyResult = !filledProfileIds.isEmpty();
        boolean allProfilesFilled = filledProfileIds.containsAll(profileIds);

        // Keep your aggregation logic for "what is the strongest status among existing results"
        // but prevent READY/RESULT_READY unless ALL profiles have results.
        List<DiagnosticStatus> statuses = resultRepository.findProcessingStatusesByOrderTestId(orderTestId);
        DiagnosticStatus aggregated = aggregate(statuses);

        DiagnosticStatus target;
        if (!hasAnyResult) {
            // no results at all
            target = DiagnosticStatus.NEW; // or PENDING if you have it
        } else if (!allProfilesFilled) {
            // some results exist but not all profiles are filled
            target = DiagnosticStatus.PARTIALLY; // make sure enum exists
        } else {
            // all profiles filled => allow aggregated status (READY / REJECTED / APPROVED ...)
            target = aggregated;
        }

        test.setProcessingStatus(target);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);

        // keep order sync
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    /**
     * Loads a result by id or throws a {@link BadRequestAlertException} if not found.
     *
     * @param id result id
     * @return existing result
     */
    private DiagnosticOrderTestResult getResult(Long id) {
        return resultRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));
    }

    /**
     * Normalizes a nullable status to {@link DiagnosticStatus#NEW}.
     *
     * @param status current status (nullable)
     * @return normalized status
     */
    private DiagnosticStatus normalize(DiagnosticStatus status) {
        return status == null ? DiagnosticStatus.NEW : status;
    }

    /**
     * Validates that a transition from {@code from} to {@code to} is allowed for results.
     *
     * @param from current status
     * @param to target status
     * @throws BadRequestAlertException if the transition is not allowed
     */
    private void ensureTransition(DiagnosticStatus from, DiagnosticStatus to) {

        if (to == DiagnosticStatus.RESULT_APPROVED) {
            if (from != DiagnosticStatus.RESULT_READY)
                throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.RESULT_REJECTED) {
            if (!(from == DiagnosticStatus.NEW
                    || from == DiagnosticStatus.RESULT_READY
                   )) {
                throw invalid(from, to);
            }
            return;
        }

        throw invalid(from, to);
    }

    /**
     * Builds a standardized invalid transition exception.
     *
     * @param from current status
     * @param to target status
     * @return exception to be thrown
     */
    private BadRequestAlertException invalid(DiagnosticStatus from, DiagnosticStatus to) {
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests_result",
                "Invalid transition " + from + " -> " + to
        );
    }

    /**
     * Aggregates multiple result statuses into a single status for the parent test.
     *
     * @param statuses list of result processing statuses
     * @return aggregated status
     */
    private DiagnosticStatus aggregate(List<DiagnosticStatus> statuses) {
        if (statuses.stream().anyMatch(s -> s == DiagnosticStatus.RESULT_REJECTED)) {
            return DiagnosticStatus.RESULT_REJECTED;
        }

        if (statuses.stream().allMatch(s -> s == DiagnosticStatus.RESULT_APPROVED)) {
            return DiagnosticStatus.RESULT_APPROVED;
        }

        if (statuses.stream().anyMatch(s -> s == DiagnosticStatus.RESULT_READY)) {
            return DiagnosticStatus.RESULT_READY;
        }

        return DiagnosticStatus.NEW;
    }
}
