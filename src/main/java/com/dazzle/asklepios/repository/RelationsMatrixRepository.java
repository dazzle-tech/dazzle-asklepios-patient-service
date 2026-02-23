package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.RelationsMatrix;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RelationsMatrixRepository extends JpaRepository<RelationsMatrix, Long> {

    Optional<RelationsMatrix> findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCodeWithLog(
            Gender firstGender,
            Gender secondGender,
            RelationType firstRelationCode
    );

    Page<RelationsMatrix> findByFirstPatientGenderWithLog(Gender firstPatientGender, Pageable pageable) ;

     Page<RelationsMatrix> findByFirstPatientGenderAndSecondPatientGenderWithLog(
            Gender firstPatientGender,
            Gender secondPatientGender,
            Pageable pageable
    ) ;
}
