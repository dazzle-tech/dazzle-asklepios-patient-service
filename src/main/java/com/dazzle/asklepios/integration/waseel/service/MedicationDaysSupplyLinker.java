package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Waseel accepts one {@code days-supply} link on a medication item.
 * Claim building attaches every supporting-info sequence afterwards, so each
 * medication would otherwise receive every other medication's duration.
 */
public final class MedicationDaysSupplyLinker {

    static final String DAYS_SUPPLY = "days-supply";

    private MedicationDaysSupplyLinker() {
    }

    public record ItemDaysSupplyLink(List<Integer> sequences, Integer daysSupplySequence) {
    }

    public static ItemDaysSupplyLink sequencesForItem(
            boolean medicationItem,
            List<Integer> sequences,
            List<Integer> mappedSequences,
            List<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        List<Integer> withoutDaysSupply = new ArrayList<>();
        if (sequences != null) {
            for (Integer sequence : sequences) {
                if (sequence == null || isDaysSupply(sequence, supportingInfo)) {
                    continue;
                }
                withoutDaysSupply.add(sequence);
            }
        }

        if (!medicationItem) {
            return new ItemDaysSupplyLink(List.copyOf(withoutDaysSupply), null);
        }

        Integer ownDaysSupply = firstDaysSupply(mappedSequences, supportingInfo);
        if (ownDaysSupply != null && !withoutDaysSupply.contains(ownDaysSupply)) {
            withoutDaysSupply.add(ownDaysSupply);
        }
        return new ItemDaysSupplyLink(List.copyOf(withoutDaysSupply), ownDaysSupply);
    }

    private static Integer firstDaysSupply(
            List<Integer> sequences,
            List<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        if (sequences == null) {
            return null;
        }
        for (Integer sequence : sequences) {
            if (isDaysSupply(sequence, supportingInfo)) {
                return sequence;
            }
        }
        return null;
    }

    private static boolean isDaysSupply(
            Integer sequence,
            List<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        if (sequence == null || supportingInfo == null) {
            return false;
        }
        for (WaseelApprovalSupportingInfo info : supportingInfo) {
            if (info == null || !sequence.equals(info.sequence()) || info.category() == null) {
                continue;
            }
            if (DAYS_SUPPLY.equals(info.category().trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
