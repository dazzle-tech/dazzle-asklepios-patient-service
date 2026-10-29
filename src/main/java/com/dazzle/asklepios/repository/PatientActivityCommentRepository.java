package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientActivityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientActivityCommentRepository extends JpaRepository<PatientActivityComment, Long> {

    List<PatientActivityComment> findByActivityIdOrderByCreatedDateAscIdAsc(Long activityId);
}
