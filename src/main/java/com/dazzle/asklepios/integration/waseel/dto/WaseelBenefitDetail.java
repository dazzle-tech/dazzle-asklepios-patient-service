package com.dazzle.asklepios.integration.waseel.dto;

import java.io.Serializable;

/**
 * One benefit or cost rule extracted from a Waseel eligibility response.
 */
public record WaseelBenefitDetail(

        String categoryKey,

        String itemName,

        String itemCode,

        String typeDisplay,

        String typeCode,

        String value,

        String unit

) implements Serializable {
}
