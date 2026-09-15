package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.UrgentCareMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UrgentCareMedicationOrderRepository extends JpaRepository<UrgentCareMedicationOrder, Long>,
        JpaSpecificationExecutor<UrgentCareMedicationOrder> {

    Page<UrgentCareMedicationOrder> findByStatus(
            MedicationOrderStatus status,
            Pageable pageable
    );

    List<UrgentCareMedicationOrder> findByOrderGroupId(Long orderGroupId);
}