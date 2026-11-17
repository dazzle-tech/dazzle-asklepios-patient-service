package com.dazzle.asklepios.repository;

public interface DentalChartRepositoryCustom {

    /**
     * Atomic compare-and-set of the chart version: increments it by one only while it still equals
     * {@code expectedVersion}. Flushes pending changes first and clears the persistence context afterwards,
     * so callers must reload the chart to see the new version.
     *
     * @return number of updated rows (0 when the version was stale or the chart does not exist)
     */
    int bumpVersionIfCurrent(Long id, Long expectedVersion);
}
