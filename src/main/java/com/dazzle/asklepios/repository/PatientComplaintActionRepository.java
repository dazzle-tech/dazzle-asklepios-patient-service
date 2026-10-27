package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientComplaintAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientComplaintActionRepository extends JpaRepository<PatientComplaintAction, Long> {}
