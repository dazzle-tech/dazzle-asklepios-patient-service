package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SurveySection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Reads the setup-owned survey sections */
@Repository
public interface SurveySectionRepository extends JpaRepository<SurveySection, Long> {

    List<SurveySection> findByIsActiveTrueOrderByDisplayOrderAscIdAsc();
}
