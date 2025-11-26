package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.RelationsMatrix;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import com.dazzle.asklepios.repository.RelationsMatrixRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class RelationsMatrixService {

    private final RelationsMatrixRepository matrixRepository;

    public RelationsMatrixService(RelationsMatrixRepository matrixRepository) {
        this.matrixRepository = matrixRepository;
    }

    public Page<RelationsMatrix> findByFirstGender(Gender firstGender, Pageable pageable) {
        return matrixRepository.findByFirstPatientGender(firstGender, pageable);
    }

    public Page<RelationsMatrix> findByFirstAndSecondGender(Gender firstGender, Gender secondGender, Pageable pageable) {
        return matrixRepository.findByFirstPatientGenderAndSecondPatientGender(firstGender, secondGender, pageable);
    }


}
