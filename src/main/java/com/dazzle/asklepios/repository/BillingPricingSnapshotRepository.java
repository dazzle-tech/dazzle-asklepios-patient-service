package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingPricingSnapshotRepository extends JpaRepository<BillingPricingSnapshot, Long> {

}