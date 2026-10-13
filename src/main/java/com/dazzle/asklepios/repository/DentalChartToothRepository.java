package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChartTooth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalChartToothRepository extends JpaRepository<DentalChartTooth, Long> {

    List<DentalChartTooth> findByChartId(Long chartId);

    void deleteByChartId(Long chartId);
}
