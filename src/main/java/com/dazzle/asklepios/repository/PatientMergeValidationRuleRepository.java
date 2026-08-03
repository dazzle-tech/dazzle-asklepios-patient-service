package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.patientMerge.PatientMergeValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientMergeValidationRuleRepository
        extends JpaRepository<PatientMergeValidationRule, Long> {

    List<PatientMergeValidationRule> findByEnabledTrue();
}
