package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByPatientIdOrderByIsCurrentDescIdDesc(Long patientId);

    Optional<Address> findFirstByPatientIdAndIsCurrentTrueOrderByIdDesc(Long patientId);

    List<Address> findByPatientIdAndIsCurrentTrue(Long patientId);
}