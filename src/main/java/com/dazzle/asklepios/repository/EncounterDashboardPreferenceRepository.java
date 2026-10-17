package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterDashboardPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EncounterDashboardPreferenceRepository extends JpaRepository<EncounterDashboardPreference, Long> {

    @EntityGraph(attributePaths = "widgets")
    Optional<EncounterDashboardPreference> findByUserIdAndEncounterId(Long userId, Long encounterId);
}
