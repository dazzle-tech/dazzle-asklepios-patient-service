package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.InsuranceSplit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class InsuranceCalculationService {

    public InsuranceSplit calculateSplit(
            BigDecimal totalAmount,
            BigDecimal copaymentPercent,
            BigDecimal copaymentCap
    ) {
        BigDecimal percentageAmount =
                totalAmount.multiply(copaymentPercent)
                        .divide(new BigDecimal("100"));

        BigDecimal patientShare =
                percentageAmount.min(copaymentCap);

        BigDecimal insuranceShare =
                totalAmount.subtract(patientShare);

        return new InsuranceSplit(patientShare, insuranceShare);
    }

}