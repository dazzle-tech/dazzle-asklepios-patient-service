package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreAuthorizationDiagnosisRepository extends JpaRepository<PreAuthorizationDiagnosis, Long> {
}