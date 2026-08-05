package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface PatientLedgerRepository extends JpaRepository<PatientLedgerEntry, Long> {

    @Query("""
    SELECT COALESCE(SUM(e.amount), 0)
    FROM PatientLedgerEntry e
    WHERE e.patientId = :patientId AND e.type = 'DEBIT'
""")
    BigDecimal sumDebitByPatient(@Param("patientId") Long patientId);


    @Query("""
    SELECT COALESCE(SUM(e.amount), 0)
    FROM PatientLedgerEntry e
    WHERE e.patientId = :patientId AND e.type = 'CREDIT'
""")
    BigDecimal sumCreditByPatient(@Param("patientId") Long patientId);

}