package com.dazzle.asklepios.integration.waseel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.integration.waseel.service.MedicationDaysSupplyLinker.ItemDaysSupplyLink;
import java.util.List;
import org.junit.jupiter.api.Test;

class MedicationDaysSupplyLinkerTest {

    @Test
    void eachMedicationKeepsOnlyItsOwnDaysSupply() {
        List<WaseelApprovalSupportingInfo> supportingInfo = List.of(
                info(1, "chief-complaint"),
                info(32, "days-supply"),
                info(33, "days-supply")
        );
        List<Integer> all = List.of(1, 32, 33);

        ItemDaysSupplyLink first = MedicationDaysSupplyLinker.sequencesForItem(
                true, all, List.of(1, 32), supportingInfo);
        ItemDaysSupplyLink second = MedicationDaysSupplyLinker.sequencesForItem(
                true, all, List.of(1, 33), supportingInfo);

        assertEquals(List.of(1, 32), first.sequences());
        assertEquals(32, first.daysSupplySequence());
        assertEquals(List.of(1, 33), second.sequences());
        assertEquals(33, second.daysSupplySequence());
    }

    @Test
    void laboratoryAndServiceItemsDoNotReceiveMedicationDurations() {
        List<WaseelApprovalSupportingInfo> supportingInfo = List.of(
                info(1, "chief-complaint"),
                info(22, "lab-test"),
                info(32, "days-supply"),
                info(33, "days-supply")
        );

        ItemDaysSupplyLink link = MedicationDaysSupplyLinker.sequencesForItem(
                false,
                List.of(1, 22, 32, 33),
                List.of(1, 22),
                supportingInfo
        );

        assertEquals(List.of(1, 22), link.sequences());
        assertNull(link.daysSupplySequence());
    }

    private static WaseelApprovalSupportingInfo info(int sequence, String category) {
        return new WaseelApprovalSupportingInfo(
                sequence, category, null, null, null, null, null, null, null, null, null, null
        );
    }
}
