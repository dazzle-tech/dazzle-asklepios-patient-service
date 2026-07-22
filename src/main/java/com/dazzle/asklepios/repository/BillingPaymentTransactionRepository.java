package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingPaymentTransactionRepository extends JpaRepository<BillingPaymentTransaction, Long> {

}