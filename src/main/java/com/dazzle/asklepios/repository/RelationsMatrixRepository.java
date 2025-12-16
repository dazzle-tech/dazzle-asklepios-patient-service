package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.RelationsMatrix;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RelationsMatrixRepository extends JpaRepository<RelationsMatrix, Long> {

    Optional<RelationsMatrix> findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(
            Gender firstGender, Gender secondGender, RelationType firstRelationCode
    );

    Page<RelationsMatrix> findByFirstPatientGender(Gender firstPatientGender, Pageable pageable);

    Page<RelationsMatrix> findByFirstPatientGenderAndSecondPatientGender(
            Gender firstPatientGender,
            Gender secondPatientGender,
            Pageable pageable
    );
}
