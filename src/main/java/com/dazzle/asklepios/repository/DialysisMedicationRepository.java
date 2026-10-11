package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DialysisMedication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DialysisMedicationRepository
        extends JpaRepository<DialysisMedication, Long> {

    List<DialysisMedication> findByDialysisSessionIdOrderByIdAsc(
            Long dialysisSessionId
    );
}
