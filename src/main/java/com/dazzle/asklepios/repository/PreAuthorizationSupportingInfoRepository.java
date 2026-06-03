package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationSupportingInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreAuthorizationSupportingInfoRepository extends JpaRepository<PreAuthorizationSupportingInfo, Long> {
}