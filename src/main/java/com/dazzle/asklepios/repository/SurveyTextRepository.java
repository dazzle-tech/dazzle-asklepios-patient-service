package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SurveyText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Reads the setup-owned texts of the survey page */
@Repository
public interface SurveyTextRepository extends JpaRepository<SurveyText, Long> {

    List<SurveyText> findByIsActiveTrue();
}
