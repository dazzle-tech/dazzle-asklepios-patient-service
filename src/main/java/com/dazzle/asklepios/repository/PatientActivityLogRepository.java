package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientActivityLogRepository extends JpaRepository<PatientActivityLog, Long> {

    List<PatientActivityLog> findByActivityIdOrderByCreatedDateDescIdDesc(Long activityId);
}
