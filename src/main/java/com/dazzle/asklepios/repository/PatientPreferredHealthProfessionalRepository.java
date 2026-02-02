package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPreferredHealthProfessional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientPreferredHealthProfessionalRepository
        extends JpaRepository<PatientPreferredHealthProfessional, Long> {

    Page<PatientPreferredHealthProfessional> findByPatient_Id(Long patientId, Pageable pageable);

}
