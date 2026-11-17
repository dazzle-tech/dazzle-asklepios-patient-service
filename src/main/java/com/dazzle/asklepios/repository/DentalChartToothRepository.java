package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.enumeration.DentalChartToothInventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DentalChartToothRepository extends JpaRepository<DentalChartTooth, Long> {

    List<DentalChartTooth> findByChartId(Long chartId);

    List<DentalChartTooth> findByChartIdAndInventoryStatus(Long chartId, DentalChartToothInventoryStatus inventoryStatus);

    void deleteByChartId(Long chartId);

    @Query("select t.chartId from DentalChartTooth t where t.id = :id")
    Optional<Long> findChartIdById(@Param("id") Long id);
}
