package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentRequest;
import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, Long>, JpaSpecificationExecutor<AppointmentRequest> {

    List<AppointmentRequest> findByFacilityId(Long facilityId);

    List<AppointmentRequest> findByPatientId(Long patientId);

    List<AppointmentRequest> findBySourceEncounterId(Long sourceEncounterId);

    List<AppointmentRequest> findByStatus(AppointmentRequestStatus status);

    List<AppointmentRequest> findByFacilityIdAndDepartmentIdIn(Long facilityId, List<Long> departmentIds);
}