package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityTemplateRepository extends JpaRepository<AvailabilityTemplate, Long> {

    List<AvailabilityTemplate> findAllByFacility(Long facility);

    List<AvailabilityTemplate> findAllByDepartment(Long department);

    List<AvailabilityTemplate> findAllByFacilityAndDepartment(Long facility, Long department);

    List<AvailabilityTemplate> findAllByStatus(TemplateStatus status);

    List<AvailabilityTemplate> findAllByTemplateType(TemplateType templateType);

    List<AvailabilityTemplate> findAllByFacilityAndDepartmentAndStatus(
            Long facility,
            Long department,
            TemplateStatus status
    );

    List<AvailabilityTemplate> findAllByFacilityAndDepartmentAndTemplateType(
            Long facility,
            Long department,
            TemplateType templateType
    );

    Optional<AvailabilityTemplate> findByIdAndStatus(Long id, TemplateStatus status);

    Optional<AvailabilityTemplate> findFirstByDepartmentAndStatusOrderByVersionNoDesc(
            Long department,
            TemplateStatus status
    );

    Optional<AvailabilityTemplate> findFirstByDepartmentAndTemplateTypeAndStatusOrderByVersionNoDesc(
            Long department,
            TemplateType templateType,
            TemplateStatus status
    );

    boolean existsByFacilityAndDepartmentAndTemplateNameAndVersionNo(
            Long facility,
            Long department,
            String templateName,
            Integer versionNo
    );
}