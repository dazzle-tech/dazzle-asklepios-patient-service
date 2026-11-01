package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SurveyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Reads the setup-owned survey questions that the patient answers */
@Repository
public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {

    /**
     * Active questions of active sections, in the order the patient sees them.
     * Sections or questions can share an order: among them the oldest (lowest id) comes first.
     */
    List<SurveyQuestion> findByIsActiveTrueAndSection_IsActiveTrueOrderBySection_DisplayOrderAscSection_IdAscDisplayOrderAscIdAsc();
}
