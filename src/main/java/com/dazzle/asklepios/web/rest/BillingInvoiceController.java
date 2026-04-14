package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.BillingInvoice;
import com.dazzle.asklepios.service.BillingInvoiceService;
import com.dazzle.asklepios.service.dto.BillingInvoiceCreateDTO;
import com.dazzle.asklepios.service.dto.BillingInvoiceUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.BillingInvoiceResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class BillingInvoiceController {

    private static final Logger LOG = LoggerFactory.getLogger(BillingInvoiceController.class);

    private final BillingInvoiceService billingInvoiceService;

    public BillingInvoiceController(BillingInvoiceService billingInvoiceService) {
        this.billingInvoiceService = billingInvoiceService;
    }

    @PostMapping("/billing/invoice")
    public ResponseEntity<BillingInvoiceResponseVM> createInvoice(
            @Valid @RequestBody BillingInvoiceCreateDTO billingInvoiceCreateDTO
    ) {
        LOG.debug("REST create BillingInvoice payload={}", billingInvoiceCreateDTO);
        BillingInvoice invoice = billingInvoiceService.create(billingInvoiceCreateDTO);
        BillingInvoiceResponseVM responseVM = BillingInvoiceResponseVM.ofEntity(invoice);
        LOG.debug("REST created BillingInvoice response={}", responseVM);

        return ResponseEntity
                .created(URI.create("/api/patient/billing/invoice/" + invoice.getId()))
                .body(responseVM);
    }

    @PutMapping("/billing/invoice/{id}")
    public ResponseEntity<BillingInvoiceResponseVM> updateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody BillingInvoiceUpdateDTO billingInvoiceUpdateDTO
    ) {
        LOG.debug("REST update BillingInvoice id={} payload={}", id, billingInvoiceUpdateDTO);
        return billingInvoiceService.update(id, billingInvoiceUpdateDTO)
                .map(BillingInvoiceResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/billing/invoice")
    public ResponseEntity<List<BillingInvoiceResponseVM>> getAllInvoices(
            @ParameterObject Pageable pageable,
            @RequestParam Long patientId
    ) {
        LOG.debug("REST list BillingInvoices pageable={} patientId={}", pageable, patientId);

        Page<BillingInvoice> page =  billingInvoiceService.findByPatientId(patientId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        List<BillingInvoiceResponseVM> body = page.getContent().stream()
                .map(BillingInvoiceResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/billing/invoice/{id}")
    public ResponseEntity<BillingInvoiceResponseVM> getInvoice(@PathVariable Long id) {
        LOG.debug("REST get BillingInvoice id={}", id);
        return billingInvoiceService.findOne(id)
                .map(BillingInvoiceResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
