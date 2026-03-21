package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SurgicalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurgicalHistoryRepository extends JpaRepository<SurgicalHistory, Long> {

    Page<SurgicalHistory> findAllByPatientId(Long patientId, Pageable pageable);
}
