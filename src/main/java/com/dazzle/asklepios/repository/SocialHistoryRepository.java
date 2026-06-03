package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SocialHistory;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialHistoryRepository extends JpaRepository<SocialHistory, Long> {

    Page<SocialHistory> findAllByPatientId(
            Long patientId,
            Pageable pageable
    );

    Page<SocialHistory> findAllByPatientIdAndStatusNot(
            Long patientId,
            PatientHistoryStatus status,
            Pageable pageable
    );
}