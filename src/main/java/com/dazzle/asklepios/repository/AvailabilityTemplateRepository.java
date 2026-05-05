package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvailabilityTemplateRepository extends JpaRepository<AvailabilityTemplate, Long> {

    @EntityGraph(attributePaths = {"workingDays"})
    Page<AvailabilityTemplate> findAllByFacilityIdAndTemplateType(Long facility, TemplateType templateType, Pageable pageable);

    @EntityGraph(attributePaths = {"workingDays"})
    Page<AvailabilityTemplate> findAllByFacilityIdAndTemplateNameIsContainingIgnoreCaseAndTemplateType(Long facility, String templateName, TemplateType templateType, Pageable pageable);

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllByParentTemplate_Id(Long parentTemplateId);

    @EntityGraph(attributePaths = {"workingDays"})
    Page<AvailabilityTemplate> findAllByDepartmentIdAndTemplateType(Long department, TemplateType templateType, Pageable pageable);

    @EntityGraph(attributePaths = {"workingDays"})
    Page<AvailabilityTemplate> findAllByFacilityIdAndStatusAndTemplateType(Long facility, TemplateStatus status, TemplateType templateType, Pageable pageable);

    @EntityGraph(attributePaths = {"workingDays"})
    Page<AvailabilityTemplate> findAllByFacilityIdAndStatusAndIsActiveTrueAndTemplateType(Long facility, TemplateStatus status, TemplateType type, Pageable pageable);

    @EntityGraph(attributePaths = {"workingDays"})
    Page<AvailabilityTemplate> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"workingDays"})
    Page<AvailabilityTemplate> findAllByFacilityIdAndDepartmentIdAndTemplateType(Long facility, Long department, TemplateType templateType, Pageable pageable);

    List<AvailabilityTemplate> findAllByParentTemplateId(Long parentTemplateId);

    List<AvailabilityTemplate> findByCopyFromTemplate_IdOrderByVersionNoDesc(Long copyFromTemplateId);

    boolean existsByParentTemplate_Id(Long parentTemplateId);

    boolean existsByCopyFromTemplate_Id(Long copyFromTemplateId);
}