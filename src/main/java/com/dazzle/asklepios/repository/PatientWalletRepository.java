package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientWalletRepository extends JpaRepository<PatientWallet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientWallet> findByPatientId(Long patientId);
}