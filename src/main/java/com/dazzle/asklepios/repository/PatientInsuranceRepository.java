package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientInsurance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientInsuranceRepository
        extends JpaRepository<PatientInsurance, Long> {

    Page<PatientInsurance> findByPatient_Id(Long patientId, Pageable pageable);

    List<PatientInsurance> findByPatient_IdIn(Collection<Long> patientIds);

    Optional<PatientInsurance>
    findFirstByPatient_IdAndPayerNphiesIdAndMemberCardId(
            Long patientId,
            String payerNphiesId,
            String memberCardId
    );

    Optional<PatientInsurance>
    findFirstByPatient_IdAndIsPrimaryTrue(Long patientId);

    Optional<PatientInsurance>
    findFirstByPatient_Id(Long patientId);

    Optional<PatientInsurance>
    findByIdAndPatient_Id(Long id, Long patientId);

    Optional<PatientInsurance>
    findByIdAndPatient_IdAndExpirationDateGreaterThanEqual(
            Long id,
            Long patientId,
            LocalDate date
    );
}
