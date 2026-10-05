package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.ClaimSettlementService;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementRowResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/claim-settlements")
public class ClaimSettlementController {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClaimSettlementController.class);

    private final ClaimSettlementService claimSettlementService;

    @GetMapping("/numbers")
    public ResponseEntity<List<String>> settlementNumbers(
            @RequestParam(value = "payerNphiesId", required = false)
            String payerNphiesId,
            @RequestParam(value = "encounterType", required = false)
            String encounterType,
            @RequestParam(value = "fromDate", required = false)
            Instant fromDate,
            @RequestParam(value = "toDate", required = false)
            Instant toDate
    ) {
        LOG.debug(
                "REST claim settlement numbers payerNphiesId={} encounterType={} fromDate={} toDate={}",
                payerNphiesId,
                encounterType,
                fromDate,
                toDate
        );

        return ResponseEntity.ok(
                claimSettlementService.listSettlementNumbers(
                        payerNphiesId,
                        encounterType,
                        fromDate,
                        toDate
                )
        );
    }

    @GetMapping
    public ResponseEntity<Page<ClaimSettlementRowResponse>> search(
            @RequestParam(value = "payerNphiesId", required = false)
            String payerNphiesId,
            @RequestParam(value = "encounterType", required = false)
            String encounterType,
            @RequestParam(value = "fromDate", required = false)
            Instant fromDate,
            @RequestParam(value = "toDate", required = false)
            Instant toDate,
            @RequestParam(value = "settlementNo", required = false)
            String settlementNo,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST claim settlements payerNphiesId={} encounterType={} fromDate={} toDate={} settlementNo={}",
                payerNphiesId,
                encounterType,
                fromDate,
                toDate,
                settlementNo
        );

        return ResponseEntity.ok(
                claimSettlementService.search(
                        payerNphiesId,
                        encounterType,
                        fromDate,
                        toDate,
                        settlementNo,
                        pageable
                )
        );
    }
}
