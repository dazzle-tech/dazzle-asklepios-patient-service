package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPayments;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientPaymentsRepository extends JpaRepository<PatientPayments, Long> {

    Optional<PatientPayments> findByEncounter_Id(Long encounterId);

    @EntityGraph(attributePaths = {
            "patient",
            "encounter",
            "plan",
            "plan.patient"
    })
    Optional<PatientPayments> findFirstByEncounterIdOrderByIdDesc(Long encounterId);

}
