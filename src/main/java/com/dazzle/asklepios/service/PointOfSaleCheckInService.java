package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PointOfSaleCheckIn;
import com.dazzle.asklepios.domain.PointOfSaleConfiguration;
import com.dazzle.asklepios.repository.PointOfSaleCheckInRepository;
import com.dazzle.asklepios.repository.PointOfSaleConfigurationRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleCheckInDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PointOfSaleCheckInService {

    private final PointOfSaleCheckInRepository pointOfSaleCheckInRepository;
    private final PointOfSaleConfigurationRepository pointOfSaleConfigurationRepository;

    public PointOfSaleCheckInDTO checkIn(
            Long configurationId
    ) {

        String currentUserLogin =
                SecurityUtils.getCurrentUserLogin()
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Current user not found",
                                        "user",
                                        "notFound"
                                )
                        );

        if (
                pointOfSaleCheckInRepository
                        .existsByUserLoginAndActiveTrue(
                                currentUserLogin
                        )
        ) {
            throw new BadRequestAlertException(
                    "User already checked in",
                    "pointOfSaleCheckIn",
                    "alreadyCheckedIn"
            );
        }



        PointOfSaleConfiguration configuration =
                pointOfSaleConfigurationRepository
                        .findById(configurationId)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "POS configuration not found",
                                        "pointOfSaleConfiguration",
                                        "notFound"
                                )
                        );

        PointOfSaleCheckIn checkIn =
                new PointOfSaleCheckIn();

        checkIn.setUserLogin(
                currentUserLogin
        );

        checkIn.setConfiguration(
                configuration
        );

        checkIn.setActive(
                Boolean.TRUE
        );

        checkIn.setCheckInDate(
                Instant.now()
        );

        checkIn =
                pointOfSaleCheckInRepository.save(
                        checkIn
                );

        return toDto(checkIn);
    }

    public void checkOut() {

        String currentUserLogin =
                SecurityUtils.getCurrentUserLogin()
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Current user not found",
                                        "user",
                                        "notFound"
                                )
                        );

        PointOfSaleCheckIn checkIn =
                pointOfSaleCheckInRepository
                        .findByUserLoginAndActiveTrue(
                                currentUserLogin
                        )
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "No active check-in found",
                                        "pointOfSaleCheckIn",
                                        "notFound"
                                )
                        );

        checkIn.setActive(
                Boolean.FALSE
        );

        checkIn.setCheckOutDate(
                Instant.now()
        );

        pointOfSaleCheckInRepository.save(
                checkIn
        );
    }

    public PointOfSaleConfiguration getCurrentUserConfiguration() {

        String currentUserLogin =
                SecurityUtils.getCurrentUserLogin()
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Current user not found",
                                        "user",
                                        "notFound"
                                )
                        );

        return pointOfSaleCheckInRepository
                .findByUserLoginAndActiveTrue(
                        currentUserLogin
                )
                .map(PointOfSaleCheckIn::getConfiguration)
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "User is not checked in to any POS device",
                                "pointOfSaleCheckIn",
                                "notCheckedIn"
                        )
                );
    }

    private PointOfSaleCheckInDTO toDto(
            PointOfSaleCheckIn entity
    ) {

        return new PointOfSaleCheckInDTO(
                entity.getId(),
                entity.getUserLogin(),
                entity.getConfiguration().getId(),
                entity.getConfiguration().getName(),
                entity.getActive(),
                entity.getCheckInDate(),
                entity.getCheckOutDate()
        );
    }
    @Transactional(readOnly = true)
    public PointOfSaleCheckInDTO getCurrentCheckIn() {

        String currentUserLogin =
                SecurityUtils.getCurrentUserLogin()
                        .orElseThrow();

        return pointOfSaleCheckInRepository
                .findByUserLoginAndActiveTrue(
                        currentUserLogin
                )
                .map(this::toDto)
                .orElse(null);
    }
    @Transactional(readOnly = true)
    public Page<PointOfSaleCheckInDTO> filter(
            Specification<PointOfSaleCheckIn> specification,
            Pageable pageable
    ) {

        return pointOfSaleCheckInRepository
                .findAll(
                        specification,
                        pageable
                )
                .map(this::toDto);
    }
}
