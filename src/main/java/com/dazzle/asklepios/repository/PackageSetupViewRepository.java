package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PackageSetupView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Read-only: package_setup belongs to setup-service. */
@Repository
public interface PackageSetupViewRepository extends JpaRepository<PackageSetupView, Long> {}
