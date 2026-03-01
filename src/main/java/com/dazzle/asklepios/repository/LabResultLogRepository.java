package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.LabResultLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabResultLogRepository extends JpaRepository<LabResultLog, Long> {

    List<LabResultLog> findAllByResultIdOrderByResultDateDesc(Long resultId);
}
