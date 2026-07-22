package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingWalletRepository extends JpaRepository<BillingWallet, Long> {

}