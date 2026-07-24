package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.FamilyHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyHistoryRepository extends JpaRepository<FamilyHistory, Long> {

    Page<FamilyHistory> findAllByPatientId(Long patientId, Pageable pageable);
}
