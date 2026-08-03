package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreAuthorizationItemRepository extends JpaRepository<PreAuthorizationItem, Long> {

    List<PreAuthorizationItem> findByPreAuthorizationIdOrderBySequenceAsc(Long preAuthorizationId);

    Optional<PreAuthorizationItem> findFirstByPreAuthorizationIdAndSequence(
            Long preAuthorizationId,
            Integer sequence
    );

    Optional<PreAuthorizationItem> findFirstByPreAuthorizationIdAndItemCodeIgnoreCase(
            Long preAuthorizationId,
            String itemCode
    );

    Optional<PreAuthorizationItem> findFirstByPreAuthorizationIdAndWaseelItemId(
            Long preAuthorizationId,
            Long waseelItemId
    );
}