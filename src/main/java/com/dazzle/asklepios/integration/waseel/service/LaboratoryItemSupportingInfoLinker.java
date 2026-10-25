package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Waseel accepts a {@code LABORATORY} item only when
 * {@code supportingInfoSequence} points at exactly one {@code lab-test} row.
 * When several results are on the claim, the item is linked only to the row
 * for its own order or diagnostic test. Another test's result is left for that test.
 * A claim that has a single {@code lab-test} row keeps that row.
 */
public final class LaboratoryItemSupportingInfoLinker {

    static final String LAB_TEST = "lab-test";

    private LaboratoryItemSupportingInfoLinker() {
    }

    public record LabTestSource(Long orderTestId, Long diagnosticTestId) {
    }

    public record ItemSupportingInfoLink(
            List<Integer> sequences,
            int labTestsOnItem,
            int labTestsOnClaim,
            Integer linkedLabTestSequence,
            boolean matchedByIdentity
    ) {
    }

    public static ItemSupportingInfoLink sequencesForItem(
            boolean laboratoryItem,
            Long diagnosticTestId,
            Long orderTestId,
            List<WaseelApprovalSupportingInfo> supportingInfo,
            List<Integer> allSequences,
            Map<Integer, LabTestSource> labTests,
            Set<Integer> consumedLabTestSequences
    ) {
        List<Integer> source = allSequences == null ? List.of() : allSequences;
        List<Integer> labSequences = labTestSequences(source, supportingInfo, labTests);
        if (!laboratoryItem) {
            return new ItemSupportingInfoLink(
                    List.copyOf(source),
                    labSequences.size(),
                    labSequences.size(),
                    null,
                    false
            );
        }
        if (labSequences.isEmpty()) {
            return new ItemSupportingInfoLink(List.copyOf(source), 0, 0, null, false);
        }
        if (labSequences.size() == 1) {
            Integer only = labSequences.get(0);
            if (consumedLabTestSequences != null) {
                consumedLabTestSequences.add(only);
            }
            return new ItemSupportingInfoLink(List.copyOf(source), 1, 1, only, true);
        }

        Integer chosen = chooseLabTest(
                diagnosticTestId,
                orderTestId,
                labSequences,
                labTests,
                consumedLabTestSequences
        );
        boolean matchedByIdentity = matchesIdentity(chosen, diagnosticTestId, orderTestId, labTests);
        if (chosen != null && consumedLabTestSequences != null) {
            consumedLabTestSequences.add(chosen);
        }

        List<Integer> linked = new ArrayList<>();
        for (Integer sequence : source) {
            if (sequence == null) {
                continue;
            }
            if (labSequences.contains(sequence)) {
                if (chosen != null && chosen.equals(sequence)) {
                    linked.add(sequence);
                }
                continue;
            }
            linked.add(sequence);
        }
        return new ItemSupportingInfoLink(
                List.copyOf(linked),
                chosen == null ? 0 : 1,
                labSequences.size(),
                chosen,
                matchedByIdentity
        );
    }

    private static Integer chooseLabTest(
            Long diagnosticTestId,
            Long orderTestId,
            List<Integer> labSequences,
            Map<Integer, LabTestSource> labTests,
            Set<Integer> consumedLabTestSequences
    ) {
        Integer byOrder = firstMatching(labSequences, labTests, consumedLabTestSequences, orderTestId, true);
        if (byOrder != null) {
            return byOrder;
        }
        return firstMatching(labSequences, labTests, consumedLabTestSequences, diagnosticTestId, false);
    }

    private static Integer firstMatching(
            List<Integer> labSequences,
            Map<Integer, LabTestSource> labTests,
            Set<Integer> consumedLabTestSequences,
            Long wantedId,
            boolean matchOrderTest
    ) {
        if (wantedId == null || labTests == null) {
            return null;
        }
        for (Integer sequence : labSequences) {
            if (sequence == null || (consumedLabTestSequences != null && consumedLabTestSequences.contains(sequence))) {
                continue;
            }
            LabTestSource source = labTests.get(sequence);
            if (source == null) {
                continue;
            }
            Long actual = matchOrderTest ? source.orderTestId() : source.diagnosticTestId();
            if (wantedId.equals(actual)) {
                return sequence;
            }
        }
        return null;
    }

    private static boolean matchesIdentity(
            Integer sequence,
            Long diagnosticTestId,
            Long orderTestId,
            Map<Integer, LabTestSource> labTests
    ) {
        if (sequence == null || labTests == null) {
            return false;
        }
        LabTestSource source = labTests.get(sequence);
        if (source == null) {
            return false;
        }
        return (orderTestId != null && orderTestId.equals(source.orderTestId()))
                || (diagnosticTestId != null && diagnosticTestId.equals(source.diagnosticTestId()));
    }

    private static List<Integer> labTestSequences(
            List<Integer> sequences,
            List<WaseelApprovalSupportingInfo> supportingInfo,
            Map<Integer, LabTestSource> labTests
    ) {
        List<Integer> labSequences = new ArrayList<>();
        for (Integer sequence : sequences) {
            if (sequence == null || labSequences.contains(sequence)) {
                continue;
            }
            if ((labTests != null && labTests.containsKey(sequence)) || isLabTestCategory(sequence, supportingInfo)) {
                labSequences.add(sequence);
            }
        }
        return labSequences;
    }

    private static boolean isLabTestCategory(
            Integer sequence,
            List<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        if (sequence == null || supportingInfo == null) {
            return false;
        }
        for (WaseelApprovalSupportingInfo info : supportingInfo) {
            if (info != null && sequence.equals(info.sequence()) && isLabTest(info.category())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLabTest(String category) {
        return category != null && LAB_TEST.equals(category.trim().toLowerCase(Locale.ROOT));
    }
}
