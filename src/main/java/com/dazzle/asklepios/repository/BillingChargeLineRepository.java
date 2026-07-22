package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingChargeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingChargeLineRepository extends JpaRepository<BillingChargeLine, Long> {

}