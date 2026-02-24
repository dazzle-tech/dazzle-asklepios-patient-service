package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.web.rest.vm.laboratory.PatientDiagnosticResultHistoryVM;
import com.dazzle.asklepios.web.rest.vm.laboratory.ProfileTestGroupedHistoryVM;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class PatientDiagnosticResultHistoryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientDiagnosticResultHistoryService.class);

    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;

    public PatientDiagnosticResultHistoryService(
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository
    ) {
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderTestResultRepository = diagnosticOrderTestResultRepository;
    }

    /**
     * Flat history (as-is list of results)
     */
    public List<PatientDiagnosticResultHistoryVM> getHistory(
            Long patientId,
            Instant fromDateTime,
            Instant toDateTime,
            Long profileTestId
    ) {
        LOG.debug(
                "[PatientDiagnosticResultHistoryService] GET_HISTORY - start. patientId={} fromDateTime={} toDateTime={} profileTestId={}",
                patientId,
                fromDateTime,
                toDateTime,
                profileTestId
        );

        List<Long> diagnosticOrderIds =
                diagnosticOrderRepository.findIdsByPatientIdAndCreatedDateBetween(patientId, fromDateTime, toDateTime);
        if (diagnosticOrderIds.isEmpty()) {
            LOG.debug(
                    "[PatientDiagnosticResultHistoryService] GET_HISTORY - no diagnostic orders found. patientId={}",
                    patientId
            );
            return List.of();
        }

        List<Long> diagnosticOrderTestIds =
                diagnosticOrderTestRepository.findIdsByOrderIdIn(diagnosticOrderIds);
        if (diagnosticOrderTestIds.isEmpty()) {
            LOG.debug(
                    "[PatientDiagnosticResultHistoryService] GET_HISTORY - no order tests found. patientId={} diagnosticOrderCount={}",
                    patientId,
                    diagnosticOrderIds.size()
            );
            return List.of();
        }

        Map<Long, Long> diagnosticOrderTestIdToDiagnosticOrderId =
                mapDiagnosticOrderTestIdToDiagnosticOrderId(diagnosticOrderTestIds);

        List<DiagnosticOrderTestResult> diagnosticTestResults;

        if (profileTestId == null) {
            diagnosticTestResults =
                    diagnosticOrderTestResultRepository.findByOrderTestIdInAndCreatedDateBetween(
                            diagnosticOrderTestIds,
                            fromDateTime,
                            toDateTime
                    );
        } else {
            diagnosticTestResults =
                    diagnosticOrderTestResultRepository.findByOrderTestIdInAndCreatedDateBetweenAndProfileTestId(
                            diagnosticOrderTestIds,
                            fromDateTime,
                            toDateTime,
                            profileTestId
                    );
        }

        if (diagnosticTestResults.isEmpty()) {
            LOG.debug(
                    "[PatientDiagnosticResultHistoryService] GET_HISTORY - no results found. patientId={} diagnosticOrderTestCount={} profileTestId={}",
                    patientId,
                    diagnosticOrderTestIds.size(),
                    profileTestId
            );
            return List.of();
        }

        List<PatientDiagnosticResultHistoryVM> patientDiagnosticResultHistoryVMS = diagnosticTestResults.stream()
                .map(resultEntity -> {
                    Long diagnosticOrderId =
                            diagnosticOrderTestIdToDiagnosticOrderId.get(resultEntity.getOrderTestId());
                    Instant resultDateTime = resultEntity.getCreatedDate();
                    return PatientDiagnosticResultHistoryVM.of(diagnosticOrderId, resultDateTime, resultEntity);
                })
                .sorted(Comparator.comparing(PatientDiagnosticResultHistoryVM::resultDate).reversed())
                .toList();

        LOG.debug(
                "[PatientDiagnosticResultHistoryService] GET_HISTORY - done. patientId={} resultCount={}",
                patientId,
                patientDiagnosticResultHistoryVMS.size()
        );

        return patientDiagnosticResultHistoryVMS;
    }

    /**
     * Grouped history by profileTestId (reviewed only: reviewDate != null)
     */
    public List<ProfileTestGroupedHistoryVM> getGroupedHistory(
            Long patientId,
            Instant fromDateTime,
            Instant toDateTime,
            Long profileTestId
    ) {
        LOG.debug(
                "[PatientDiagnosticResultHistoryService] GET_GROUPED_HISTORY - start. patientId={} fromDateTime={} toDateTime={} profileTestId={}",
                patientId,
                fromDateTime,
                toDateTime,
                profileTestId
        );

        List<PatientDiagnosticResultHistoryVM> resultHistoryVMS =
                getHistory(patientId, fromDateTime, toDateTime, profileTestId);

        if (resultHistoryVMS.isEmpty()) {
            LOG.debug(
                    "[PatientDiagnosticResultHistoryService] GET_GROUPED_HISTORY - no flat results to group. patientId={}",
                    patientId
            );
            return List.of();
        }

        List<PatientDiagnosticResultHistoryVM> reviewedResultsOnly =
                resultHistoryVMS.stream()
                        .filter(resultVm -> resultVm.reviewDate() != null)
                        .toList();

        if (reviewedResultsOnly.isEmpty()) {
            LOG.debug(
                    "[PatientDiagnosticResultHistoryService] GET_GROUPED_HISTORY - no reviewed results found. patientId={} flatResultCount={}",
                    patientId,
                    resultHistoryVMS.size()
            );
            return List.of();
        }

        List<ProfileTestGroupedHistoryVM> groupedHistory = groupResultsByProfileTestId(reviewedResultsOnly);

        LOG.debug(
                "[PatientDiagnosticResultHistoryService] GET_GROUPED_HISTORY - done. patientId={} groupedCount={}",
                patientId,
                groupedHistory.size()
        );

        return groupedHistory;
    }

    private Map<Long, Long> mapDiagnosticOrderTestIdToDiagnosticOrderId(List<Long> diagnosticOrderTestIds) {
        List<Object[]> idAndOrderIdByIdIn = diagnosticOrderTestRepository.findIdAndOrderIdByIdIn(diagnosticOrderTestIds);

        Map<Long, Long> diagnosticOrderTestIdToDiagnosticOrderId = new HashMap<>(idAndOrderIdByIdIn.size());
        for (Object[] row : idAndOrderIdByIdIn) {
            Long diagnosticOrderTestId = (Long) row[0];
            Long diagnosticOrderId = (Long) row[1];
            diagnosticOrderTestIdToDiagnosticOrderId.put(diagnosticOrderTestId, diagnosticOrderId);
        }

        return diagnosticOrderTestIdToDiagnosticOrderId;
    }

    private List<ProfileTestGroupedHistoryVM> groupResultsByProfileTestId(
            List<PatientDiagnosticResultHistoryVM> patientDiagnosticResultHistoryVMS
    ) {
        Map<Long, List<PatientDiagnosticResultHistoryVM>> resultsByProfileTestId =
                patientDiagnosticResultHistoryVMS.stream()
                        .collect(Collectors.groupingBy(
                                PatientDiagnosticResultHistoryVM::profileTestId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        return resultsByProfileTestId.entrySet().stream()
                .map(entry -> {
                    Long profileTestId = entry.getKey();
                    List<PatientDiagnosticResultHistoryVM> resultsForThisProfileTest = entry.getValue();

                    resultsForThisProfileTest.sort(
                            Comparator.comparing(PatientDiagnosticResultHistoryVM::resultDate).reversed()
                    );

                    PatientDiagnosticResultHistoryVM latestResult = resultsForThisProfileTest.get(0);

                    return new ProfileTestGroupedHistoryVM(
                            profileTestId,
                            latestResult.resultDate(),
                            latestResult.processingStatus(),
                            resultsForThisProfileTest
                    );
                })
                .toList();
    }
}
