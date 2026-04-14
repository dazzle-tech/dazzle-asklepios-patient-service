package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientUccMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientUccMedicationOrderRepository extends JpaRepository<PatientUccMedicationOrder, Long>,
        JpaSpecificationExecutor<PatientUccMedicationOrder> {

    Page<PatientUccMedicationOrder> findByStatus(MedicationOrderStatus status, Pageable pageable);
}