package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientInsuranceCoverage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientInsuranceCoverageRepository extends JpaRepository<PatientInsuranceCoverage, Long> {

    Page<PatientInsuranceCoverage> findByInsuranceId(Long insuranceId, Pageable pageable);

    long countByInsuranceId(Long insuranceId);

    void deleteByInsuranceId(Long insuranceId);
}

