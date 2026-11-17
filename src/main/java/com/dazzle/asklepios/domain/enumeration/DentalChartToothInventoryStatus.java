package com.dazzle.asklepios.domain.enumeration;

/**
 * Whether a chart tooth is part of the chart's current inventory. Teeth with clinical references
 * are never deleted: they stay as REMOVED or REPLACED and are excluded from the active inventory.
 */
public enum DentalChartToothInventoryStatus {
    ACTIVE,
    REMOVED,
    REPLACED
}
