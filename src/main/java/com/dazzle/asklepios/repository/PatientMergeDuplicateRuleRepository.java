package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientMergeDuplicateRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientMergeDuplicateRuleRepository
        extends JpaRepository<PatientMergeDuplicateRule, Long> {

    List<PatientMergeDuplicateRule>
    findByTableConfigIdAndEnabledTrueOrderBySortOrderAscIdAsc(Long tableConfigId);
}
