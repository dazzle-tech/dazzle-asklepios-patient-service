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
    Logger LOG = LoggerFactory.getLogger(RelationsMatrixRepository.class);

    Optional<RelationsMatrix> findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(
            Gender firstGender, Gender secondGender, RelationType firstRelationCode
    );

    Page<RelationsMatrix> findByFirstPatientGender(Gender firstPatientGender, Pageable pageable);

    Page<RelationsMatrix> findByFirstPatientGenderAndSecondPatientGender(
            Gender firstPatientGender,
            Gender secondPatientGender,
            Pageable pageable
    );

    default Optional<RelationsMatrix> findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCodeWithLog(
            Gender firstGender,
            Gender secondGender,
            RelationType firstRelationCode
    ) {
        LOG.debug(
                "[FIND_FIRST] RelationsMatrix firstGender={} secondGender={} firstRelationCode={}",
                firstGender,
                secondGender,
                firstRelationCode
        );
        Optional<RelationsMatrix> result =
                findFirstByFirstPatientGenderAndSecondPatientGenderAndFirstRelationCode(firstGender, secondGender, firstRelationCode);
        LOG.debug(
                "[FIND_FIRST] RelationsMatrix resultFound={} firstGender={} secondGender={} firstRelationCode={}",
                result.isPresent(),
                firstGender,
                secondGender,
                firstRelationCode
        );
        return result;
    }

    default Page<RelationsMatrix> findByFirstPatientGenderWithLog(Gender firstPatientGender, Pageable pageable) {
        LOG.debug("[FIND_BY_FIRST_GENDER] RelationsMatrix firstPatientGender={} pageable={}", firstPatientGender, pageable);
        return findByFirstPatientGender(firstPatientGender, pageable);
    }

    default Page<RelationsMatrix> findByFirstPatientGenderAndSecondPatientGenderWithLog(
            Gender firstPatientGender,
            Gender secondPatientGender,
            Pageable pageable
    ) {
        LOG.debug(
                "[FIND_BY_GENDERS] RelationsMatrix firstPatientGender={} secondPatientGender={} pageable={}",
                firstPatientGender,
                secondPatientGender,
                pageable
        );
        return findByFirstPatientGenderAndSecondPatientGender(firstPatientGender, secondPatientGender, pageable);
    }
}
