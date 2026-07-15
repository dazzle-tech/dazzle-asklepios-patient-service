package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.FinancialDocumentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialDocumentItemRepository extends JpaRepository<FinancialDocumentItem, Long> {

    List<FinancialDocumentItem> findByDocument_Id(Long documentId);

    List<FinancialDocumentItem> findByDocumentId(Long documentId);
}
