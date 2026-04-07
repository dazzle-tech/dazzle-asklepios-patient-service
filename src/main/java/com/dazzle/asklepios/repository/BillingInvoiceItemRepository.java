package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingInvoiceItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface BillingInvoiceItemRepository extends JpaRepository<BillingInvoiceItem, Long> {

    Page<BillingInvoiceItem> findByInvoiceId(Long invoiceId, Pageable pageable);
}
