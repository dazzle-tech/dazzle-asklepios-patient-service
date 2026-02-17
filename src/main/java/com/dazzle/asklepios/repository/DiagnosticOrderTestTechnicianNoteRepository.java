package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DiagnosticOrderTestTechnicianNoteRepository
        extends JpaRepository<DiagnosticOrderTestTechnicianNote, Long> {

    Page<DiagnosticOrderTestTechnicianNote> findByOrderTestId(Long orderTestId, Pageable pageable);

    Page<DiagnosticOrderTestTechnicianNote> findByOrderId(Long orderId, Pageable pageable);
    boolean existsByOrderTestId(Long orderTestId);
    @Query("""
    select distinct n.orderTestId
    from DiagnosticOrderTestTechnicianNote n
    where n.orderTestId in :orderTestIds
""")
    List<Long> findDistinctOrderTestIdsIn(List<Long> orderTestIds);

}
