package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentWaitingList;
import com.dazzle.asklepios.domain.enumeration.WaitingListStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentWaitingListRepository extends JpaRepository<AppointmentWaitingList, Long> {

    List<AppointmentWaitingList> findByFacilityIdAndDepartmentIdAndStatusOrderByCreatedDateAsc(
            Long facilityId,
            Long departmentId,
            WaitingListStatus status
    );
}