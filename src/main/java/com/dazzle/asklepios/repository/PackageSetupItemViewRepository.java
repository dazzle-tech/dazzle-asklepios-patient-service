package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PackageSetupItemView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Read-only: package_setup_item belongs to setup-service. */
@Repository
public interface PackageSetupItemViewRepository extends JpaRepository<PackageSetupItemView, Long> {

    List<PackageSetupItemView> findByPackageSetupIdOrderByIdAsc(Long packageSetupId);
}
