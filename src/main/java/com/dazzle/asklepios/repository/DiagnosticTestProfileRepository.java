package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticTestProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiagnosticTestProfileRepository extends JpaRepository<DiagnosticTestProfile, Long> {
}
