package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingRefundRepository extends JpaRepository<BillingRefund, Long> {

}