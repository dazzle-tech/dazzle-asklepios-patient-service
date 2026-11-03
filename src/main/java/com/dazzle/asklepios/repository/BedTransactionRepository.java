package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BedTransaction;
import com.dazzle.asklepios.domain.enumeration.BedTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;

public interface BedTransactionRepository extends JpaRepository<BedTransaction, Long>, JpaSpecificationExecutor<BedTransaction> {


    Page<BedTransaction> findByTransactionType(BedTransactionType transactionType, Pageable pageable);

    Page<BedTransaction> findByToDepartmentIdAndTransactionDateBetween(
            Long toDepartmentId,
            Instant from,
            Instant to,
            Pageable pageable
    );
}