package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplateAllowedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityTemplateAllowedServiceRepository extends JpaRepository<AvailabilityTemplateAllowedService, Long> {

    void deleteByInterval_Id(Long intervalId);
}