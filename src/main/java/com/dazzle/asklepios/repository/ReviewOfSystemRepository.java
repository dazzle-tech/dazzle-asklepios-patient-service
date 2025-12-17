package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ReviewOfSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewOfSystemRepository extends JpaRepository<ReviewOfSystem, Long> {

    List<ReviewOfSystem> findByEncounterId(Long encounterId);

    List<ReviewOfSystem> findByEncounterIdAndBodySystem(Long encounterId, String bodySystem);

    Optional<ReviewOfSystem> findByEncounterIdAndBodySystemAndSystemDetail(
            Long encounterId,
            String bodySystem,
            String systemDetail
    );

    void deleteByEncounterIdAndBodySystemAndSystemDetail(Long encounterId, String bodySystem, String systemDetail);
}
