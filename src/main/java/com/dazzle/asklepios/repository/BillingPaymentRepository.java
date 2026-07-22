package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, Long> {

}
