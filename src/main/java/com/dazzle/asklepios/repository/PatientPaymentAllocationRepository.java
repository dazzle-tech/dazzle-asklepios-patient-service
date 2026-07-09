package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPaymentAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface PatientPaymentAllocationRepository
        extends JpaRepository<PatientPaymentAllocation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientPaymentAllocation> findByPaymentIdAndChargeId(
            Long paymentId,
            Long chargeId
    );

    @Query("""
        select coalesce(sum(allocation.paidFromAmount + allocation.paidFromBalance), 0)
        from PatientPaymentAllocation allocation
        where allocation.chargeId = :chargeId
    """)
    BigDecimal sumAllocatedForCharge(Long chargeId);


    @Query("""
    SELECT COALESCE(SUM(a.paidFromAmount), 0)
    FROM PatientPaymentAllocation a
    WHERE a.chargeId = :chargeId
""")
    BigDecimal sumPaidForCharge(Long chargeId);
}