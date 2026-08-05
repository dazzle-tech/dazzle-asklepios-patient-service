package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

}
