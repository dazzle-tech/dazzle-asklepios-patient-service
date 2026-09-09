package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientEncounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface EncounterListRepository
        extends JpaRepository<PatientEncounter, Long>,
        JpaSpecificationExecutor<PatientEncounter> {
}