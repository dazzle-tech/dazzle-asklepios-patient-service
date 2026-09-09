package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PointOfSaleConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointOfSaleConfigurationRepository
        extends JpaRepository<PointOfSaleConfiguration, Long>,
        JpaSpecificationExecutor<PointOfSaleConfiguration> {

    Optional<PointOfSaleConfiguration> findByTerminalId(String terminalId);

}
