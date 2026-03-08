package com.dazzle.asklepios.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientLedgerLockRepository {

    @Query(value = "select pg_try_advisory_xact_lock(?1)", nativeQuery = true)
    Boolean tryLockPatientLedger(Long patientId);
}