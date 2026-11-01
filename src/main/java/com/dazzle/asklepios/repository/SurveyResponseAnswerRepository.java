package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.SurveyResponseAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SurveyResponseAnswerRepository extends JpaRepository<SurveyResponseAnswer, Long> {

    /**
     * Answers of these responses in the order the patient saw them: section order, then question order;
     * sections or questions sharing an order keep the oldest (lowest id) first.
     * The question and its section are loaded in the same query.
     */
    @EntityGraph(attributePaths = {"question", "question.section"})
    List<SurveyResponseAnswer>
    findByResponseIdInOrderByQuestion_Section_DisplayOrderAscQuestion_Section_IdAscQuestion_DisplayOrderAscQuestion_IdAsc(
            Collection<Long> responseIds);
}
