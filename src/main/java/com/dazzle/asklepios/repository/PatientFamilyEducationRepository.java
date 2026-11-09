package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientFamilyEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientFamilyEducationRepository extends JpaRepository<PatientFamilyEducation, Long> {

    Optional<PatientFamilyEducation> findFirstByEncounter_IdOrderByIdDesc(Long encounterId);
}
