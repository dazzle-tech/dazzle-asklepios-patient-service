package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.ReferralRequest;
import com.dazzle.asklepios.service.ReferralRequestService;
import com.dazzle.asklepios.service.dto.referralRequest.ReferralRequestCreateDTO;
import com.dazzle.asklepios.service.dto.referralRequest.ReferralRequestUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class ReferralRequestController {

    private static final Logger LOG = LoggerFactory.getLogger(ReferralRequestController.class);

    private final ReferralRequestService referralRequestService;

    public ReferralRequestController(ReferralRequestService referralRequestService) {
        this.referralRequestService = referralRequestService;
    }

    @PostMapping("/referral-request")
    public ResponseEntity<ReferralRequest> create(
            @Valid @RequestBody @NotNull ReferralRequestCreateDTO dto
    ) {
        LOG.debug("REST create ReferralRequest payload={}", dto);

        ReferralRequest created = referralRequestService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/setup/referral-request/" + created.getId()))
                .body(created);
    }

    @PutMapping("/referral-request/{id}")
    public ResponseEntity<ReferralRequest> update(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody @NotNull ReferralRequestUpdateDTO dto
    ) {
        LOG.debug("REST update ReferralRequest id={} payload={}", id, dto);

        return referralRequestService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/referral-request/by-encounter/{encounterId}")
    public ResponseEntity<List<ReferralRequest>> getByEncounter(
            @PathVariable @NotNull Long encounterId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST get ReferralRequests by encounterId={} pageable={}", encounterId, pageable);

        Page<ReferralRequest> page = referralRequestService.getByEncounter(encounterId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
    @PutMapping("/referral-request/{id}/accept")
    public ResponseEntity<ReferralRequest> accept(@PathVariable @NotNull Long id) {

        LOG.debug("REST accept ReferralRequest id={}", id);

        ReferralRequest accepted = referralRequestService.accept(id);

        return ResponseEntity.ok(accepted);
    }

    @PutMapping("/referral-request/{id}/reject")
    public ResponseEntity<ReferralRequest> reject(
            @PathVariable @NotNull Long id,
            @RequestParam("reason") @NotBlank String reason
    ) {

        LOG.debug("REST reject ReferralRequest id={} reason={}", id, reason);

        ReferralRequest rejected = referralRequestService.reject(id, reason);

        return ResponseEntity.ok(rejected);
    }
    @GetMapping("/referral-request/by-to-facility/{toFacilityId}/created-between")
    public ResponseEntity<List<ReferralRequest>> getByToFacilityCreatedBetween(
            @PathVariable @NotNull Long toFacilityId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list ReferralRequests by toFacilityId={} from={} to={} pageable={}",
                toFacilityId, from, to, pageable);

        Page<ReferralRequest> page = referralRequestService.getByToFacilityAndCreatedDateRange(
                toFacilityId, from, to, pageable
        );

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}