package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DialysisFlowReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DialysisFlowReadingRepository
        extends JpaRepository<DialysisFlowReading, Long> {

    List<DialysisFlowReading> findByDialysisSessionIdOrderByTimeAsc(
            Long dialysisSessionId
    );
}
