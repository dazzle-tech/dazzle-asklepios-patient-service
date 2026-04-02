package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityTemplateRepository extends JpaRepository<AvailabilityTemplate, Long> {

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllByFacilityIdAndTemplateType(Long facility, TemplateType templateType);

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllByFacilityIdAndTemplateNameIsContainingIgnoreCase(Long facility, String templateName);

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllByParentTemplate_Id(Long parentTemplateId);

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllByDepartmentId(Long department);

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllByFacilityIdAndStatus(Long facility, TemplateStatus status);

    @EntityGraph(attributePaths = {"workingDays"})
    Optional<AvailabilityTemplate> findWithWorkingDaysById(Long id);

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllBy();

    @EntityGraph(attributePaths = {"workingDays"})
    List<AvailabilityTemplate> findAllByFacilityIdAndDepartmentId(Long facility, Long department);

}