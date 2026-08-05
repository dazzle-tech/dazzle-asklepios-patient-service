package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.InsuranceSplit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

public interface InsuranceCalculationService {

    InsuranceSplit calculateSplit(
            BigDecimal totalAmount,
            BigDecimal copaymentPercent,
            BigDecimal copaymentCap
    );

}