package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPaymentServices;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientPaymentServicesRepository extends JpaRepository<PatientPaymentServices, Long> {

    List<PatientPaymentServices> findByPayment_Id(Long paymentId);

}
