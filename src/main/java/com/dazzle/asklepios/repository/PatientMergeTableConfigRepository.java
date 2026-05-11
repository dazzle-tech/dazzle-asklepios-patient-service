package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientMergeTableConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PatientMergeTableConfigRepository extends JpaRepository<PatientMergeTableConfig, Long>,
        JpaSpecificationExecutor<PatientMergeTableConfig> {

    List<PatientMergeTableConfig> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
