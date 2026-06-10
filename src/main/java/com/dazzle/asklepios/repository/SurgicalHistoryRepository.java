package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SurgicalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurgicalHistoryRepository extends JpaRepository<SurgicalHistory, Long> {
    List<SurgicalHistory> findAllByPatientId(Long patientId);
    Page<SurgicalHistory> findAllByPatientId(Long patientId, Pageable pageable);
}
