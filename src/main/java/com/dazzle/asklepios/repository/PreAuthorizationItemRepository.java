package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreAuthorizationItemRepository extends JpaRepository<PreAuthorizationItem, Long> {
}