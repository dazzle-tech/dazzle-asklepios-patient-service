package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PreAuthorizationCareTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PreAuthorizationCareTeamRepository extends JpaRepository<PreAuthorizationCareTeam, Long> {
}