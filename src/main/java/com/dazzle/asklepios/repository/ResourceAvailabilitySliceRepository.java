package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ResourceAvailabilitySlice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceAvailabilitySliceRepository extends JpaRepository<ResourceAvailabilitySlice, String> {

    List<ResourceAvailabilitySlice> findByResourceKeyAndIsValidTrueAndDeletedAtIsNull(String resourceKey);

    List<ResourceAvailabilitySlice> findByResourceKeyAndFacilityKeyAndIsValidTrueAndDeletedAtIsNull(
            String resourceKey, String facilityKey);

    List<ResourceAvailabilitySlice> findByResourceKeyAndFacilityKeyAndDayOfWeekAndIsValidTrueAndDeletedAtIsNull(
            String resourceKey, String facilityKey, String dayOfWeek);

    Page<ResourceAvailabilitySlice> findByIsValidTrueAndDeletedAtIsNull(Pageable pageable);

    @Query("SELECT s FROM ResourceAvailabilitySlice s WHERE s.resourceKey = :resourceKey " +
           "AND s.isValid = true AND s.deletedAt IS NULL " +
           "ORDER BY CAST(s.dayOfWeek AS INTEGER), CAST(s.startTimeMinutes AS INTEGER)")
    List<ResourceAvailabilitySlice> findActiveSlicesByResourceKey(@Param("resourceKey") String resourceKey);
}

