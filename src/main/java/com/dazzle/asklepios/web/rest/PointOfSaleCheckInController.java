package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.PointOfSaleCheckIn;
import com.dazzle.asklepios.service.PointOfSaleCheckInService;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleCheckInDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PointOfSaleCheckInController {

    private final PointOfSaleCheckInService pointOfSaleCheckInService;

    @PostMapping("/pos/check-in/{configurationId}")
    public ResponseEntity<PointOfSaleCheckInDTO> checkIn(
            @PathVariable @NotNull Long configurationId
    ) {

        PointOfSaleCheckInDTO result =
                pointOfSaleCheckInService.checkIn(
                        configurationId
                );

        return ResponseEntity.ok(
                result
        );
    }

    @PostMapping("/pos/check-out")
    public ResponseEntity<Void> checkOut() {

        pointOfSaleCheckInService.checkOut();

        return ResponseEntity.ok()
                .build();
    }

    @GetMapping("/pos/current")
    public ResponseEntity<PointOfSaleCheckInDTO> getCurrentCheckIn() {

        PointOfSaleCheckInDTO result =
                pointOfSaleCheckInService
                        .getCurrentCheckIn();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/pos/history")
    public ResponseEntity<List<PointOfSaleCheckInDTO>> history(

            @RequestParam(name = "userLogin", required = false)
            String userLogin,

            @RequestParam(name = "configurationId", required = false)
            Long configurationId,

            @RequestParam(name = "active", required = false)
            Boolean active,
            @RequestParam(name = "configurationActive", required = false)
            Boolean configurationActive
            ,
            @RequestParam(name = "checkInDateFrom", required = false)
            Instant checkInDateFrom,

            @RequestParam(name = "checkInDateTo", required = false)
            Instant checkInDateTo,

            @RequestParam(name = "checkOutDateFrom", required = false)
            Instant checkOutDateFrom,

            @RequestParam(name = "checkOutDateTo", required = false)
            Instant checkOutDateTo,

            @ParameterObject Pageable pageable
    ) {

        Specification<PointOfSaleCheckIn> specification =
                (root, query, cb) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    if (
                            userLogin != null &&
                                    !userLogin.isBlank()
                    ) {

                        predicates.add(
                                cb.like(
                                        cb.lower(
                                                root.get("userLogin")
                                        ),
                                        "%" +
                                                userLogin
                                                        .trim()
                                                        .toLowerCase()
                                                + "%"
                                )
                        );
                    }

                    if (
                            configurationId != null
                    ) {

                        predicates.add(
                                cb.equal(
                                        root.get("configuration")
                                                .get("id"),
                                        configurationId
                                )
                        );
                    }

                    if (
                            active != null
                    ) {

                        predicates.add(
                                cb.equal(
                                        root.get("active"),
                                        active
                                )
                        );
                    }
                    if (configurationActive != null) {

                        predicates.add(
                                cb.equal(
                                        root.get("configuration")
                                                .get("isActive"),
                                        configurationActive
                                )
                        );
                    }
                    if (
                            checkInDateFrom != null
                    ) {

                        predicates.add(
                                cb.greaterThanOrEqualTo(
                                        root.get("checkInDate"),
                                        checkInDateFrom
                                )
                        );
                    }

                    if (
                            checkInDateTo != null
                    ) {

                        predicates.add(
                                cb.lessThanOrEqualTo(
                                        root.get("checkInDate"),
                                        checkInDateTo
                                )
                        );
                    }

                    if (
                            checkOutDateFrom != null
                    ) {

                        predicates.add(
                                cb.greaterThanOrEqualTo(
                                        root.get("checkOutDate"),
                                        checkOutDateFrom
                                )
                        );
                    }

                    if (
                            checkOutDateTo != null
                    ) {

                        predicates.add(
                                cb.lessThanOrEqualTo(
                                        root.get("checkOutDate"),
                                        checkOutDateTo
                                )
                        );
                    }

                    return cb.and(
                            predicates.toArray(
                                    new Predicate[0]
                            )
                    );
                };

        Page<PointOfSaleCheckInDTO> page =
                pointOfSaleCheckInService.filter(
                        specification,
                        pageable
                );

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder
                                .fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }
}