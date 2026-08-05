package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreAuthorizationRequestRepository extends JpaRepository<PreAuthorizationRequest, Long> {

    Optional<PreAuthorizationRequest> findFirstByApprovalRequestIdOrderByIdDesc(Long approvalRequestId);

    Optional<PreAuthorizationRequest> findFirstByApprovalResponseIdOrderByIdDesc(Long approvalResponseId);

    List<PreAuthorizationRequest> findByEncounterIdOrderByIdDesc(Long encounterId);
}