package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientCharge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientChargeRepository extends JpaRepository<PatientCharge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientCharge> findByEncounterId(Long encounterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PatientCharge> findByPatientIdAndRemainingGreaterThanOrderByCreatedDateAscIdAsc(
            Long patientId,
            BigDecimal remaining
    );


    @Query("""
        select coalesce(sum(c.remaining), 0)
        from PatientCharge c
        where c.patientId = :patientId
    """)
    BigDecimal sumTotalRemainingByPatient(@Param("patientId") Long patientId);

    @Query("""
        select coalesce(sum(c.remaining), 0)
        from PatientCharge c
        where c.patientId = :patientId
          and c.remaining > 0
    """)
    BigDecimal sumOpenRemainingByPatient(@Param("patientId") Long patientId);


}