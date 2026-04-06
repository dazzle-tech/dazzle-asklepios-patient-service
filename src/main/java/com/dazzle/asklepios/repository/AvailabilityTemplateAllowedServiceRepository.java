package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplateAllowedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvailabilityTemplateAllowedServiceRepository extends JpaRepository<AvailabilityTemplateAllowedService, Long> {

    void deleteByInterval_Id(Long intervalId);
    void deleteByTemplate_Id(Long templateId);
    List<AvailabilityTemplateAllowedService> findAllByTemplate_IdAndIntervalIsNull(Long templateId);

}