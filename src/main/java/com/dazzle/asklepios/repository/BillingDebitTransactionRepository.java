package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingDebitTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingDebitTransactionRepository extends JpaRepository<BillingDebitTransaction, Long> {

}