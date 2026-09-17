package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PointOfSaleCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointOfSaleCheckInRepository
        extends JpaRepository<PointOfSaleCheckIn, Long>,
        JpaSpecificationExecutor<PointOfSaleCheckIn> {


    Optional<PointOfSaleCheckIn> findByUserLoginAndActiveTrue(
            String userLogin
    );

    Optional<PointOfSaleCheckIn> findByConfigurationIdAndActiveTrue(
            Long configurationId
    );

    boolean existsByUserLoginAndActiveTrue(
            String userLogin
    );
    boolean existsByConfigurationIdAndActiveTrue(
            Long configurationId
    );
}