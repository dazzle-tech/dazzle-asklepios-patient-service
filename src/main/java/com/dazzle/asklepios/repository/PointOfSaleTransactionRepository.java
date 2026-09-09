package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PointOfSaleTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PointOfSaleTransactionRepository
        extends JpaRepository<PointOfSaleTransaction, Long>,
        JpaSpecificationExecutor<PointOfSaleTransaction> {


    List<PointOfSaleTransaction> findAllByPatientId(Long patientId);
    Optional<PointOfSaleTransaction> findByOrderId(String orderId);
    Optional<PointOfSaleTransaction>
    findByExternalTransactionId(
            String externalTransactionId
    );

    Optional<PointOfSaleTransaction> findByRrn(String rrn);
}