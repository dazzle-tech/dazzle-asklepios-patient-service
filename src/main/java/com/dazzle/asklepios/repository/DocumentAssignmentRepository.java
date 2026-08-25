package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DocumentAssignment;
import com.dazzle.asklepios.domain.enumeration.DocumentTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentAssignmentRepository extends JpaRepository<DocumentAssignment, Long> {
    List<DocumentAssignment> findAllByDocumentDefinitionId(
            Long documentId
    );

    List<DocumentAssignment> findAllByTargetTypeAndTargetIdAndActiveTrue(
            DocumentTargetType targetType,
            Long targetId
    );

    List<DocumentAssignment> findAllByTargetTypeAndTargetIdIsNull(
            DocumentTargetType targetType
    );

    List<DocumentAssignment> findAllByTargetTypeAndTargetIdIn(
            DocumentTargetType targetType,
            List<Long> targetIds
    );
    boolean existsByDocumentDefinition_IdAndTargetTypeAndTargetIdIsNull(
            Long documentId,
            DocumentTargetType targetType
    );

    boolean existsByDocumentDefinition_IdAndTargetTypeAndTargetIdIsNotNull(
            Long documentId,
            DocumentTargetType targetType
    );

    boolean existsByDocumentDefinition_IdAndTargetTypeAndTargetId(
            Long documentId,
            DocumentTargetType targetType,
            Long targetId
    );
}
