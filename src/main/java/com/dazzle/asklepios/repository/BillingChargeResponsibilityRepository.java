package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingChargeResponsibilityRepository extends JpaRepository<BillingChargeResponsibility, Long> {

}