package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientServiceAndProductRepository extends JpaRepository<PatientServiceAndProduct, Long> {

    Page<PatientServiceAndProduct> findAllByEncounterId(Long encounterId, Pageable pageable);

}

