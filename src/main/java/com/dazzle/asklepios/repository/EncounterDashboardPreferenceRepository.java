package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterDashboardPreference;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EncounterDashboardPreferenceRepository extends JpaRepository<EncounterDashboardPreference, Long> {

    @EntityGraph(attributePaths = "widgets")
    Optional<EncounterDashboardPreference> findByUserIdAndEncounterId(Long userId, Long encounterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select preference from EncounterDashboardPreference preference where preference.userId = :userId and preference.encounterId = :encounterId"
    )
    Optional<EncounterDashboardPreference> findByUserIdAndEncounterIdForUpdate(
        @Param("userId") Long userId,
        @Param("encounterId") Long encounterId
    );
}
