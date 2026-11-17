package com.dazzle.asklepios.repository.projection;

/**
 * Typed result of the grouped open-planned-treatment count for one chart tooth.
 */
public record OpenTreatmentCount(Long toothId, Long openCount) {
}
