package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.RegistrationPendingPaymentService;
import com.dazzle.asklepios.service.dto.billing.RegistrationPendingPaymentChangesDTO;
import com.dazzle.asklepios.service.dto.billing.RegistrationPendingPaymentEncounterDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/patient/billing/registration-pending-items")
public class RegistrationPendingPaymentController {

    private static final Logger LOG =
            LoggerFactory.getLogger(RegistrationPendingPaymentController.class);

    private final RegistrationPendingPaymentService registrationPendingPaymentService;

    public RegistrationPendingPaymentController(
            RegistrationPendingPaymentService registrationPendingPaymentService
    ) {
        this.registrationPendingPaymentService = registrationPendingPaymentService;
    }

    @GetMapping
    public ResponseEntity<List<RegistrationPendingPaymentEncounterDTO>> searchRegistrationPendingItems(
            @RequestParam @NotNull Long facilityId,
            @RequestParam(required = false) String patientName,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST search Registration Pending Payment encounters facilityId={} patientName={} pageable={}",
                facilityId,
                patientName,
                pageable
        );

        Page<RegistrationPendingPaymentEncounterDTO> page =
                registrationPendingPaymentService.searchRegistrationPendingItems(
                        facilityId,
                        patientName,
                        pageable
                );

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/changes")
    public ResponseEntity<RegistrationPendingPaymentChangesDTO> getRegistrationPendingItemChanges(
            @RequestParam @NotNull Long facilityId,
            @RequestParam(required = false, defaultValue = "0") Long afterId
    ) {
        LOG.debug(
                "REST get Registration Pending Payment changes facilityId={} afterId={}",
                facilityId,
                afterId
        );

        return ResponseEntity.ok(
                registrationPendingPaymentService.getRegistrationPendingItemChanges(
                        facilityId,
                        afterId
                )
        );
    }
}
