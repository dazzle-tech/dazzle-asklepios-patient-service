package com.dazzle.asklepios.integration.waseel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.integration.waseel.service.LaboratoryItemSupportingInfoLinker.ItemSupportingInfoLink;
import com.dazzle.asklepios.integration.waseel.service.LaboratoryItemSupportingInfoLinker.LabTestSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LaboratoryItemSupportingInfoLinkerTest {

    @Test
    void laboratoryItemKeepsClinicalRowsAndOnlyItsOwnLabTest() {
        Set<Integer> consumed = new LinkedHashSet<>();

        ItemSupportingInfoLink link = link(true, 22L, null, claimSupportingInfo(), consumed);

        assertEquals(List.of(1, 2, 5, 7, 22), link.sequences());
        assertEquals(1, link.labTestsOnItem());
        assertEquals(22, link.linkedLabTestSequence());
        assertTrue(link.matchedByIdentity());
        assertEquals(Set.of(22), consumed);
    }

    @Test
    void eachLaboratoryItemReceivesADifferentResultWhenSeveralExist() {
        List<WaseelApprovalSupportingInfo> supportingInfo = claimSupportingInfo();
        Set<Integer> consumed = new LinkedHashSet<>();

        ItemSupportingInfoLink hba1c = link(true, 22L, null, supportingInfo, consumed);
        ItemSupportingInfoLink hdl = link(true, 19L, null, supportingInfo, consumed);

        assertEquals(List.of(1, 2, 5, 7, 22), hba1c.sequences());
        assertEquals(List.of(1, 2, 5, 7, 19), hdl.sequences());
    }

    @Test
    void orderTestIdWinsWhenDiagnosticTestIdDoesNotMatch() {
        Set<Integer> consumed = new LinkedHashSet<>();

        ItemSupportingInfoLink link = link(true, 999L, 2200L, claimSupportingInfo(), consumed);

        assertEquals(List.of(1, 2, 5, 7, 22), link.sequences());
        assertTrue(link.matchedByIdentity());
    }

    @Test
    void duplicateOrdersOfTheSameTestDoNotShareOneResultRow() {
        List<WaseelApprovalSupportingInfo> supportingInfo = List.of(
                info(1, "chief-complaint"),
                info(15, "lab-test"),
                info(16, "lab-test")
        );
        Map<Integer, LabTestSource> labTests = new LinkedHashMap<>();
        labTests.put(15, new LabTestSource(1L, 90L));
        labTests.put(16, new LabTestSource(2L, 90L));
        Set<Integer> consumed = new LinkedHashSet<>();

        ItemSupportingInfoLink first = LaboratoryItemSupportingInfoLinker.sequencesForItem(
                true, 90L, null, supportingInfo, List.of(1, 15, 16), labTests, consumed);
        ItemSupportingInfoLink second = LaboratoryItemSupportingInfoLinker.sequencesForItem(
                true, 90L, null, supportingInfo, List.of(1, 15, 16), labTests, consumed);

        assertEquals(List.of(1, 15), first.sequences());
        assertEquals(List.of(1, 16), second.sequences());
    }

    @Test
    void unmatchedLaboratoryItemStillKeepsExactlyOneLabTest() {
        Set<Integer> consumed = new LinkedHashSet<>();
        List<WaseelApprovalSupportingInfo> supportingInfo = claimSupportingInfo();

        ItemSupportingInfoLink first = link(true, 999L, null, supportingInfo, consumed);
        ItemSupportingInfoLink second = link(true, 998L, null, supportingInfo, consumed);

        assertEquals(List.of(1, 2, 5, 7, 19), first.sequences());
        assertEquals(1, first.labTestsOnItem());
        assertFalse(first.matchedByIdentity());
        assertEquals(List.of(1, 2, 5, 7, 22), second.sequences());
        assertEquals(1, second.labTestsOnItem());
    }

    @Test
    void singleLabTestOnTheClaimStaysLinked() {
        List<WaseelApprovalSupportingInfo> supportingInfo = List.of(
                info(1, "chief-complaint"),
                info(2, "investigation-result"),
                info(3, "lab-test")
        );
        Map<Integer, LabTestSource> labTests = Map.of(3, new LabTestSource(3L, 3L));

        ItemSupportingInfoLink link = LaboratoryItemSupportingInfoLinker.sequencesForItem(
                true,
                null,
                null,
                supportingInfo,
                List.of(1, 2, 3),
                labTests,
                new LinkedHashSet<>()
        );

        assertEquals(List.of(1, 2, 3), link.sequences());
        assertEquals(1, link.labTestsOnItem());
    }

    @Test
    void serviceMedicationAndImagingItemsKeepTheirExistingSequences() {
        List<WaseelApprovalSupportingInfo> supportingInfo = new ArrayList<>(claimSupportingInfo());
        supportingInfo.add(info(40, "days-supply"));
        List<Integer> all = List.of(1, 2, 5, 7, 19, 22, 31, 40);

        ItemSupportingInfoLink link = LaboratoryItemSupportingInfoLinker.sequencesForItem(
                false,
                22L,
                null,
                supportingInfo,
                all,
                labTestsBySequence(),
                new LinkedHashSet<>()
        );

        assertEquals(all, link.sequences());
    }

    private static ItemSupportingInfoLink link(
            boolean laboratoryItem,
            Long diagnosticTestId,
            Long orderTestId,
            List<WaseelApprovalSupportingInfo> supportingInfo,
            Set<Integer> consumed
    ) {
        return LaboratoryItemSupportingInfoLinker.sequencesForItem(
                laboratoryItem,
                diagnosticTestId,
                orderTestId,
                supportingInfo,
                sequencesOf(supportingInfo),
                labTestsBySequence(),
                consumed
        );
    }

    private static List<WaseelApprovalSupportingInfo> claimSupportingInfo() {
        return List.of(
                info(1, "chief-complaint"),
                info(2, "history-of-present-illness"),
                info(5, "investigation-result"),
                info(7, "pulse"),
                info(19, "lab-test"),
                info(22, "lab-test"),
                info(31, "lab-test")
        );
    }

    private static Map<Integer, LabTestSource> labTestsBySequence() {
        Map<Integer, LabTestSource> labTests = new LinkedHashMap<>();
        labTests.put(19, new LabTestSource(1900L, 19L));
        labTests.put(22, new LabTestSource(2200L, 22L));
        labTests.put(31, new LabTestSource(3100L, 31L));
        return labTests;
    }

    private static List<Integer> sequencesOf(List<WaseelApprovalSupportingInfo> supportingInfo) {
        return supportingInfo.stream().map(WaseelApprovalSupportingInfo::sequence).toList();
    }

    private static WaseelApprovalSupportingInfo info(int sequence, String category) {
        return new WaseelApprovalSupportingInfo(
                sequence,
                category,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
