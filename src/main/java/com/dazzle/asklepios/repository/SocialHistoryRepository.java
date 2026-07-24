package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SocialHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialHistoryRepository extends JpaRepository<SocialHistory, Long> {
    Optional<SocialHistory> findTopByPatientIdOrderByCreatedDateDesc(Long patientId);
    Page<SocialHistory> findAllByPatientId(Long patientId, Pageable pageable);
}
