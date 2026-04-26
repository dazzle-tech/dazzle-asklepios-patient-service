package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingInvoice;
import com.dazzle.asklepios.domain.BillingInvoiceItem;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.repository.BillingInvoiceItemRepository;
import com.dazzle.asklepios.repository.BillingInvoiceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.BillingInvoiceItemCreateDTO;
import com.dazzle.asklepios.service.dto.BillingInvoiceItemUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BillingInvoiceItemService {

    private static final Logger LOG = LoggerFactory.getLogger(BillingInvoiceItemService.class);

    private final BillingInvoiceItemRepository billingInvoiceItemRepository;
    private final BillingInvoiceRepository billingInvoiceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    public BillingInvoiceItemService(BillingInvoiceItemRepository billingInvoiceItemRepository,
                                     BillingInvoiceRepository billingInvoiceRepository,
                                     PatientServiceAndProductRepository patientServiceAndProductRepository) {
        this.billingInvoiceItemRepository = billingInvoiceItemRepository;
        this.billingInvoiceRepository = billingInvoiceRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
    }

    public BillingInvoiceItem create(BillingInvoiceItemCreateDTO billingInvoiceItemCreateDTO) {
        LOG.debug("Request to create BillingInvoiceItem : {}", billingInvoiceItemCreateDTO);

        BillingInvoice invoice = billingInvoiceRepository.findById(billingInvoiceItemCreateDTO.invoiceId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "BillingInvoice not found with id " + billingInvoiceItemCreateDTO.invoiceId(),
                        "billingInvoice",
                        "notfound"
                ));
        //TODO: add validation for nurseServiceProductId from setup service

        BillingInvoiceItem item = BillingInvoiceItem.builder()
                .invoice(invoice)
                .nurseServiceProductId(billingInvoiceItemCreateDTO.nurseServiceProductId())
                .code(billingInvoiceItemCreateDTO.code())
                .quantity(billingInvoiceItemCreateDTO.quantity())
                .unitPrice(billingInvoiceItemCreateDTO.unitPrice())
                .totalPrice(billingInvoiceItemCreateDTO.totalPrice())
                .currency(billingInvoiceItemCreateDTO.currency())
                .build();

        BillingInvoiceItem saved = billingInvoiceItemRepository.save(item);
        PatientServiceAndProduct serviceAndProduct = patientServiceAndProductRepository.findById(billingInvoiceItemCreateDTO.nurseServiceProductId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "nurseServiceProductId not found with id " + billingInvoiceItemCreateDTO.nurseServiceProductId(),
                        "billingInvoice",
                        "notfound"
                ));
        serviceAndProduct.setIsBilled(true);
        serviceAndProduct.setBillingInvoiceId(invoice.getId());
        serviceAndProduct.setBillingInvoiceItemId(saved.getId());

        LOG.debug("Created BillingInvoiceItem: {}", saved);
        return saved;
    }

    public Optional<BillingInvoiceItem> update(Long id, BillingInvoiceItemUpdateDTO billingInvoiceItemUpdateDTO) {
        LOG.debug("Request to update BillingInvoiceItem id={} with {}", id, billingInvoiceItemUpdateDTO);

        BillingInvoiceItem item = billingInvoiceItemRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "BillingInvoiceItem not found with id " + id,
                        "billingInvoiceItem",
                        "notfound"
                ));

        if (billingInvoiceItemUpdateDTO.invoiceId() != null) {
            BillingInvoice invoice = billingInvoiceRepository.findById(billingInvoiceItemUpdateDTO.invoiceId())
                    .orElseThrow(() -> new BadRequestAlertException(
                            "BillingInvoice not found with id " + billingInvoiceItemUpdateDTO.invoiceId(),
                            "billingInvoice",
                            "notfound"
                    ));
            item.setInvoice(invoice);
        }
        if (billingInvoiceItemUpdateDTO.nurseServiceProductId() != null)
            item.setNurseServiceProductId(billingInvoiceItemUpdateDTO.nurseServiceProductId());
        if (billingInvoiceItemUpdateDTO.code() != null) item.setCode(billingInvoiceItemUpdateDTO.code());
        if (billingInvoiceItemUpdateDTO.quantity() != null) item.setQuantity(billingInvoiceItemUpdateDTO.quantity());
        if (billingInvoiceItemUpdateDTO.unitPrice() != null) item.setUnitPrice(billingInvoiceItemUpdateDTO.unitPrice());
        if (billingInvoiceItemUpdateDTO.totalPrice() != null)
            item.setTotalPrice(billingInvoiceItemUpdateDTO.totalPrice());
        if (billingInvoiceItemUpdateDTO.currency() != null) item.setCurrency(billingInvoiceItemUpdateDTO.currency());

        BillingInvoiceItem updated = billingInvoiceItemRepository.save(item);
        LOG.debug("Updated BillingInvoiceItem: {}", updated);

        return Optional.of(updated);
    }

    @Transactional(readOnly = true)
    public Page<BillingInvoiceItem> findAll(Pageable pageable) {
        LOG.debug("Request to get BillingInvoiceItems: {}", pageable);
        return billingInvoiceItemRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<BillingInvoiceItem> findByInvoiceId(Long invoiceId, Pageable pageable) {
        LOG.debug("Request to get by invoiceId: {}", invoiceId);
        return billingInvoiceItemRepository.findByInvoiceId(invoiceId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<BillingInvoiceItem> findOne(Long id) {
        LOG.debug("Request to get BillingInvoiceItem : {}", id);
        return billingInvoiceItemRepository.findById(id);
    }
}
