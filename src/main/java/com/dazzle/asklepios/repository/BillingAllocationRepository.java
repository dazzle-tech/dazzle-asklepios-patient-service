package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingAllocationRepository extends JpaRepository<BillingAllocation, Long> {

}
