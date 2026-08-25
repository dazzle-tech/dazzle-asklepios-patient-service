package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DocumentVersion;
import com.dazzle.asklepios.domain.enumeration.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findAllByDocumentDefinition_IdOrderByCreatedDateDesc(Long documentDefinitionId);

    Optional<DocumentVersion> findByDocumentDefinition_IdIsAndStatusIs(Long documentDefinitionId, DocumentStatus status);

    Optional<DocumentVersion> findFirstByDocumentDefinition_IdOrderByVersionDesc(Long documentDefinitionId);

    List<DocumentVersion> findAllByDocumentDefinition_Id(Long documentId);

    Optional<DocumentVersion> findFirstByDocumentDefinition_IdAndStatus(Long documentDefinitionId, DocumentStatus status);
}
