package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface PatientServiceAndProductRepositoryCustom {

    long countDistinctEncounters(Specification<PatientServiceAndProduct> specification);

    long countDistinctPatients(Specification<PatientServiceAndProduct> specification);

    List<Long> findDistinctEncounterIds(
            Specification<PatientServiceAndProduct> specification,
            Pageable pageable
    );
}
