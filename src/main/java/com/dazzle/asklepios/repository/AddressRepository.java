package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByPatientIdOrderByIsCurrentDescIdDesc(Long patientId);

    Optional<Address> findFirstByPatientIdAndIsCurrentTrueOrderByIdDesc(Long patientId);

    @Modifying
    @Query("UPDATE Address a SET a.isCurrent = false WHERE a.patient.id = :patientId")
    void resetIsCurrentForPatient(@Param("patientId") Long patientId);
}
