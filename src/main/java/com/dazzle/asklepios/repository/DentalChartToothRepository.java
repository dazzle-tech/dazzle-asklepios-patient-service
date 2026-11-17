package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.enumeration.DentalChartToothInventoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalChartToothRepository extends JpaRepository<DentalChartTooth, Long> {

    List<DentalChartTooth> findByChartId(Long chartId);

    List<DentalChartTooth> findByChartIdIn(List<Long> chartIds);

    List<DentalChartTooth> findByChartIdAndInventoryStatus(Long chartId, DentalChartToothInventoryStatus inventoryStatus);

    void deleteByChartId(Long chartId);
}
