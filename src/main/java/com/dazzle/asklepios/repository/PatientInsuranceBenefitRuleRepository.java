package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientInsuranceBenefitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientInsuranceBenefitRuleRepository
        extends JpaRepository<PatientInsuranceBenefitRule, Long> {

    List<PatientInsuranceBenefitRule> findByPatientInsuranceIdOrderByBenefitCategoryAsc(
            Long patientInsuranceId
    );

    void deleteByPatientInsuranceId(Long patientInsuranceId);
}
