package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.InsurancePayerReceivablesService;
import com.dazzle.asklepios.service.dto.billing.InsurancePayerReceivablesSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/insurance-receivables")
public class InsurancePayerReceivablesController {

    private static final Logger LOG =
            LoggerFactory.getLogger(InsurancePayerReceivablesController.class);

    private final InsurancePayerReceivablesService insurancePayerReceivablesService;

    @GetMapping("/payers")
    public ResponseEntity<List<InsurancePayerReceivablesSummaryResponse>>
    summarizeByPayer(
            @RequestParam(value = "facilityId", required = false)
            Long facilityId
    ) {
        LOG.debug(
                "REST request to summarize insurance receivables by payer facilityId={}",
                facilityId
        );

        return ResponseEntity.ok(
                insurancePayerReceivablesService.summarizeByPayer(
                        facilityId
                )
        );
    }
}
