package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvailabilityTemplateLogRepository extends JpaRepository<AvailabilityTemplateLog, Long> {

    List<AvailabilityTemplateLog> findAllByTemplateIdOrderByLogDateDesc(Long templateId);

    void deleteByTemplateIdOrCopyFromTemplateIdOrParentTemplateId(Long templateId, Long copyFromTemplateId, Long parentTemplateId);
}
