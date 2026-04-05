package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillingInvoiceItemRepository extends JpaRepository<BillingInvoiceItem, Long> {

    List<BillingInvoiceItem> findByInvoiceId(Long invoiceId);
}
