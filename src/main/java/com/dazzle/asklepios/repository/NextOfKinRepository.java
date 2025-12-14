package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.NextOfKin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NextOfKinRepository extends JpaRepository<NextOfKin, Long> {

    List<NextOfKin> findByPatientId(Long patientId);

}

