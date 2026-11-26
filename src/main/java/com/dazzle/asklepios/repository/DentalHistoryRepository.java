package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DentalHistoryRepository extends JpaRepository<DentalHistory, Long> {

    Page<DentalHistory> findByPatient_Id(Long patientId, Pageable pageable);
}
