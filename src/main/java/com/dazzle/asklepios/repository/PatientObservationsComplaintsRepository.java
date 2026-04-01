package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientObservationsComplaints;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.dazzle.asklepios.domain.VitalSigns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientObservationsComplaintsRepository
        extends JpaRepository<PatientObservationsComplaints, Long> {

    Optional<PatientObservationsComplaints>
    findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);
    Set<PatientObservationsComplaints> findDistinctByEncounterIdIn(List<Long> encounterIds);}
