package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientMergeMasterDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PatientMergeMasterDecisionRepository extends JpaRepository<PatientMergeMasterDecision, Long>,
        JpaSpecificationExecutor<PatientMergeMasterDecision> {

    List<PatientMergeMasterDecision> findByMergeLogId(Long mergeLogId);
}

