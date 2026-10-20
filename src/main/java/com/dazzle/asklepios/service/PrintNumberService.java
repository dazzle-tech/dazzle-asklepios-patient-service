package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Analyzer;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticTestLaboratory;
import com.dazzle.asklepios.domain.TubeColorPrintCode;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.repository.AnalyzerRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticTestLaboratoryRepository;
import com.dazzle.asklepios.repository.TubeColorPrintCodeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Assigns printNumber once per order, at submit time - the analytics-service
 * "Print Selected" print flow only ever reads this value, never computes it.
 * This keeps grouping decisions (which tests share a label) fixed as soon as
 * the order is submitted, instead of being recalculated on every print.
 */
@Service
@RequiredArgsConstructor
public class PrintNumberService {

    private static final Logger LOG = LoggerFactory.getLogger(PrintNumberService.class);

    private final DiagnosticOrderTestRepository orderTestRepository;
    private final DiagnosticTestLaboratoryRepository laboratoryRepository;
    private final AnalyzerRepository analyzerRepository;
    private final TubeColorPrintCodeRepository tubeColorPrintCodeRepository;

    private static final String NO_TUBE_COLOR_CODE = "000";

    /**
     * Groups the order's (non-cancelled) tests by (analyzer, tubeColor, tubeType)
     * and assigns each group a printNumber ({analyzerCode}{7-digit orderId}{3-digit
     * tubeColor code}). The tubeColor code comes from the tube_color_print_code
     * mapping table, filled in by hand (no admin screen) - a group whose tubeColor
     * has no row there is left without a printNumber, same as a missing analyzer,
     * so the print flow reports it as "missing definition" instead of guessing a
     * code. A group with no tubeColor at all (incomplete tube info) still prints,
     * using "000" as a placeholder since it was never meant to distinguish by color.
     * Tests with no analyzer assigned are left without a printNumber - the
     * print flow treats that as "missing definition" and skips them. A
     * printNumber already assigned on any member of a group is kept as-is
     * and never recomputed/overwritten.
     */
    @Transactional
    public void assignPrintNumbersForOrder(Long orderId) {
        List<DiagnosticOrderTest> orderTests = orderTestRepository.findByOrderIdInAndStatusNot(
                List.of(orderId),
                DiagnosticOrderTestStatus.CANCELLED
        );

        if (orderTests.isEmpty()) {
            LOG.debug("[PrintNumber] No non-cancelled tests found for orderId={}", orderId);
            return;
        }

        List<Long> testIds = orderTests.stream()
                .map(DiagnosticOrderTest::getTestId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, DiagnosticTestLaboratory> labByTestId = laboratoryRepository.findByTest_IdIn(testIds).stream()
                .filter(lab -> lab.getTest() != null)
                .collect(Collectors.toMap(lab -> lab.getTest().getId(), lab -> lab));

        LinkedHashMap<String, List<DiagnosticOrderTest>> groups = new LinkedHashMap<>();
        for (DiagnosticOrderTest orderTest : orderTests) {
            DiagnosticTestLaboratory lab = labByTestId.get(orderTest.getTestId());

            if (lab == null || lab.getAnalyzerId() == null) {
                continue;
            }

            String groupKey = (lab.getTubeColor() != null && lab.getTubeType() != null)
                    ? lab.getAnalyzerId() + "|" + lab.getTubeColor() + "|" + lab.getTubeType()
                    : "STANDALONE-" + orderTest.getId();

            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(orderTest);
        }

        if (groups.isEmpty()) {
            LOG.warn(
                    "[PrintNumber] No eligible tests to assign printNumber for orderId={} (no analyzer configured on any test)",
                    orderId
            );
            return;
        }

        Map<Long, Analyzer> analyzerById = analyzerRepository.findAllById(
                groups.values().stream()
                        .flatMap(List::stream)
                        .map(ot -> labByTestId.get(ot.getTestId()).getAnalyzerId())
                        .distinct()
                        .toList()
        ).stream().collect(Collectors.toMap(Analyzer::getId, a -> a));

        List<DiagnosticOrderTest> toSave = new ArrayList<>();

        for (List<DiagnosticOrderTest> groupTests : groups.values()) {
            boolean alreadyAssigned = groupTests.stream().anyMatch(ot -> ot.getPrintNumber() != null);
            if (alreadyAssigned) {
                continue;
            }

            DiagnosticTestLaboratory representativeLab = labByTestId.get(groupTests.get(0).getTestId());
            Long analyzerId = representativeLab.getAnalyzerId();
            String analyzerCode = Optional.ofNullable(analyzerById.get(analyzerId))
                    .map(Analyzer::getCode)
                    .orElse("");

            String tubeColor = representativeLab.getTubeColor();
            String tubeColorCode;
            if (tubeColor == null) {
                tubeColorCode = NO_TUBE_COLOR_CODE;
            } else {
                Optional<TubeColorPrintCode> colorMapping =
                        tubeColorPrintCodeRepository.findByTubeColorKey(tubeColor);
                if (colorMapping.isEmpty()) {
                    LOG.warn(
                            "[PrintNumber] Skipping group for orderId={} analyzerId={} tubeColor={} - no print code mapped for this tube color",
                            orderId,
                            analyzerId,
                            tubeColor
                    );
                    continue;
                }
                tubeColorCode = colorMapping.get().getCode();
            }

            String printNumber = analyzerCode + String.format("%07d", orderId) + tubeColorCode;

            groupTests.forEach(ot -> {
                ot.setPrintNumber(printNumber);
                toSave.add(ot);
            });
        }

        orderTestRepository.saveAll(toSave);

        LOG.info(
                "[PrintNumber] Assigned printNumber to {} order test(s) for orderId={}",
                toSave.size(),
                orderId
        );
    }
}
