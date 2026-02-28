package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientRelation;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRelationRepository extends JpaRepository<PatientRelation, Long> {

    boolean existsByPatient_IdAndRelativePatient_IdAndRelationType(
            Long patientId,
            Long relativeId,
            RelationType relationType
    );
    Optional<PatientRelation> findByPatient_IdAndRelativePatient_IdAndRelationType(
            Long patientId,
            Long relativeId,
            RelationType relationType
    );
    Page<PatientRelation> findByPatient_Id(Long patientId, Pageable pageable);
    boolean existsByPatient_IdAndRelationType(Long patientId, RelationType relationType);

}
