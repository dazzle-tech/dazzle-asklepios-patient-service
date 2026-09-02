package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ClaimItemRepository extends JpaRepository<ClaimItem, Long> {

    List<ClaimItem> findByClaimRequestIdOrderBySequenceAsc(Long claimRequestId);

    List<ClaimItem> findByClaimRequestIdIn(Collection<Long> claimRequestIds);
}
