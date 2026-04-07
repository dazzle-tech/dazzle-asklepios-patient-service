package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.BillingInvoiceItem;
import com.dazzle.asklepios.service.BillingInvoiceItemService;
import com.dazzle.asklepios.service.dto.BillingInvoiceItemCreateDTO;
import com.dazzle.asklepios.service.dto.BillingInvoiceItemUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.BillingInvoiceItemResponseVM;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class BillingInvoiceItemController {

    private static final Logger LOG = LoggerFactory.getLogger(BillingInvoiceItemController.class);

    private final BillingInvoiceItemService billingInvoiceItemService;

    public BillingInvoiceItemController(BillingInvoiceItemService billingInvoiceItemService) {
        this.billingInvoiceItemService = billingInvoiceItemService;
    }

    @PostMapping("/billing/invoice-item")
    public ResponseEntity<BillingInvoiceItemResponseVM> createInvoiceItem(
            @Valid @RequestBody BillingInvoiceItemCreateDTO billingInvoiceItemCreateDTO
    ) {
        LOG.debug("REST create BillingInvoiceItem payload={}", billingInvoiceItemCreateDTO);
        BillingInvoiceItem item = billingInvoiceItemService.create(billingInvoiceItemCreateDTO);
        BillingInvoiceItemResponseVM responseVM = BillingInvoiceItemResponseVM.ofEntity(item);

        return ResponseEntity
                .created(URI.create("/api/patient/billing/invoice-item/" + item.getId()))
                .body(responseVM);
    }

    @PutMapping("/billing/invoice-item/{id}")
    public ResponseEntity<BillingInvoiceItemResponseVM> updateInvoiceItem(
            @PathVariable Long id,
            @Valid @RequestBody BillingInvoiceItemUpdateDTO billingInvoiceItemUpdateDTO
    ) {
        LOG.debug("REST update BillingInvoiceItem id={} payload={}", id, billingInvoiceItemUpdateDTO);
        return billingInvoiceItemService.update(id, billingInvoiceItemUpdateDTO)
                .map(BillingInvoiceItemResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/billing/invoice-item")
    public ResponseEntity<List<BillingInvoiceItemResponseVM>> getAllInvoiceItems(
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list BillingInvoiceItems pageable={}", pageable);
        Page<BillingInvoiceItem> page = billingInvoiceItemService.findAll(pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        List<BillingInvoiceItemResponseVM> body = page.getContent().stream()
                .map(BillingInvoiceItemResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/billing/by-invoice/{invoiceId}")
    public ResponseEntity<List<BillingInvoiceItemResponseVM>> getInvoiceItemsByInvoiceId(
            @PathVariable Long invoiceId,
            @ParameterObject Pageable pageable) {
        LOG.debug("REST get BillingInvoiceItem invoiceId={}", invoiceId);
        Page<BillingInvoiceItem> page = billingInvoiceItemService.findByInvoiceId(invoiceId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        List<BillingInvoiceItemResponseVM> body = page.getContent().stream()
                .map(BillingInvoiceItemResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/billing/invoice-item/{id}")
    public ResponseEntity<BillingInvoiceItemResponseVM> getInvoiceItem(@PathVariable Long id) {
        LOG.debug("REST get BillingInvoiceItem id={}", id);
        return billingInvoiceItemService.findOne(id)
                .map(BillingInvoiceItemResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
