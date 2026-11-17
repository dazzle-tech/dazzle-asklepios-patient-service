package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChartInventoryAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DentalChartInventoryAuditRepository extends JpaRepository<DentalChartInventoryAudit, Long> {

    List<DentalChartInventoryAudit> findByChartIdOrderByCreatedDateDescIdDesc(Long chartId);
}
