package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreAuthorizationRequestRepository extends JpaRepository<PreAuthorizationRequest, Long> {
}