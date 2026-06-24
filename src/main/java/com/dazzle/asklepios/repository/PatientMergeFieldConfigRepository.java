package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientMergeFieldConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PatientMergeFieldConfigRepository extends JpaRepository<PatientMergeFieldConfig, Long>,
        JpaSpecificationExecutor<PatientMergeFieldConfig> {

    List<PatientMergeFieldConfig> findByTableConfigIdAndEnabledTrue(Long tableConfigId);
}
