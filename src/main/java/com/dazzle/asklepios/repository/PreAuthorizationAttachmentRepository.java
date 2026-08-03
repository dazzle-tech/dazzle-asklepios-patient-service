package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreAuthorizationAttachmentRepository extends JpaRepository<PreAuthorizationAttachment, Long> {

    List<PreAuthorizationAttachment> findByPreAuthorizationIdAndDeletedAtIsNullOrderByCreatedDateDesc(
            Long preAuthorizationId
    );

    Optional<PreAuthorizationAttachment> findByIdAndDeletedAtIsNull(Long id);

    Optional<PreAuthorizationAttachment> findByIdAndPreAuthorizationIdAndDeletedAtIsNull(
            Long id,
            Long preAuthorizationId
    );
}
