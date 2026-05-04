package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingInvoice;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.BillingInvoiceStatus;
import com.dazzle.asklepios.repository.BillingInvoiceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.BillingInvoiceCreateDTO;
import com.dazzle.asklepios.service.dto.BillingInvoiceUpdateDTO;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class BillingInvoiceService {

    private static final Logger LOG = LoggerFactory.getLogger(BillingInvoiceService.class);

    private final BillingInvoiceRepository billingInvoiceRepository;
    private final FacilityHelper facilityHelper;
    private final PatientRepository patientRepository;

    public BillingInvoiceService(BillingInvoiceRepository billingInvoiceRepository, FacilityHelper facilityHelper, PatientRepository patientRepository) {
        this.billingInvoiceRepository = billingInvoiceRepository;
        this.facilityHelper = facilityHelper;
        this.patientRepository = patientRepository;
    }

    public BillingInvoice create(BillingInvoiceCreateDTO billingInvoiceCreateDTO) {
        LOG.debug("Request to create BillingInvoice : {}", billingInvoiceCreateDTO);
        loadPatient(billingInvoiceCreateDTO.patientId());
        facilityHelper.validateFacilityExists(billingInvoiceCreateDTO.facilityId());

        BillingInvoice invoice = BillingInvoice.builder()
                .patientId(billingInvoiceCreateDTO.patientId())
                .facilityId(billingInvoiceCreateDTO.facilityId())
                .status(BillingInvoiceStatus.PENDING)
                .totalAmount(billingInvoiceCreateDTO.totalAmount())
                .paidAmount(billingInvoiceCreateDTO.paidAmount() != null ? billingInvoiceCreateDTO.paidAmount() : billingInvoiceCreateDTO.totalAmount())
                .balanceAmount(billingInvoiceCreateDTO.balanceAmount() != null ? billingInvoiceCreateDTO.balanceAmount() : billingInvoiceCreateDTO.totalAmount().subtract(billingInvoiceCreateDTO.paidAmount() != null ? billingInvoiceCreateDTO.paidAmount() : billingInvoiceCreateDTO.totalAmount()))
                .currency(billingInvoiceCreateDTO.currency())
                .build();

        BillingInvoice saved = billingInvoiceRepository.save(invoice);
        LOG.debug("Created BillingInvoice: {}", saved);
        return saved;
    }

    public Optional<BillingInvoice> update(Long id, BillingInvoiceUpdateDTO invoiceupdate) {
        LOG.debug("Request to update BillingInvoice id={} with {}", id, invoiceupdate);

        BillingInvoice invoice = billingInvoiceRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "BillingInvoice not found with id " + id,
                        "billingInvoice",
                        "notfound"
                ));

        if (invoiceupdate.patientId() != null) {
            loadPatient(invoiceupdate.patientId());

            invoice.setPatientId(invoiceupdate.patientId());
        }
        if (invoiceupdate.facilityId() != null) {
            facilityHelper.validateFacilityExists(invoiceupdate.facilityId());

            invoice.setFacilityId(invoiceupdate.facilityId());
        }
        if (invoiceupdate.status() != null) invoice.setStatus(invoiceupdate.status());
        if (invoiceupdate.totalAmount() != null) invoice.setTotalAmount(invoiceupdate.totalAmount());
        if (invoiceupdate.paidAmount() != null) invoice.setPaidAmount(invoiceupdate.paidAmount());
        if (invoiceupdate.balanceAmount() != null) invoice.setBalanceAmount(invoiceupdate.balanceAmount());
        if (invoiceupdate.currency() != null) invoice.setCurrency(invoiceupdate.currency());

        BillingInvoice updated = billingInvoiceRepository.save(invoice);
        LOG.debug("Updated BillingInvoice: {}", updated);

        return Optional.of(updated);
    }

    @Transactional(readOnly = true)
    public Page<BillingInvoice> findAll(Pageable pageable) {
        LOG.debug("Request to get BillingInvoices: {}", pageable);
        return billingInvoiceRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<BillingInvoice> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("Request to get BillingInvoices by patientId={} pageable={}", patientId, pageable);
        return billingInvoiceRepository.findByPatientId(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<BillingInvoice> findOne(Long id) {
        LOG.debug("Request to get BillingInvoice : {}", id);
        return billingInvoiceRepository.findById(id);
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "BillingInvoice",
                        "patient.notfound"
                ));
    }
}
