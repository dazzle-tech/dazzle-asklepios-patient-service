package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingReservationRepository extends JpaRepository<BillingReservation, Long> {

}