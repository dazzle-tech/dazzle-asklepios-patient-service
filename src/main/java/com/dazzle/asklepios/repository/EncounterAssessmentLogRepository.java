package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterAssessmentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterAssessmentLogRepository
        extends JpaRepository<EncounterAssessmentLog, Long> {

    List<EncounterAssessmentLog> findByEncounterAssessmentIdOrderByLogDateDesc(
            Long encounterAssessmentId
    );
}
