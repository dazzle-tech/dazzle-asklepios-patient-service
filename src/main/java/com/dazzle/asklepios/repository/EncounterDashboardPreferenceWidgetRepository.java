package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterDashboardPreferenceWidget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EncounterDashboardPreferenceWidgetRepository
    extends JpaRepository<EncounterDashboardPreferenceWidget, Long> {

    void deleteByPreference_Id(Long preferenceId);
}
