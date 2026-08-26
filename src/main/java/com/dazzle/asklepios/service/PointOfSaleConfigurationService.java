package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PointOfSaleConfiguration;
import com.dazzle.asklepios.repository.PointOfSaleCheckInRepository;
import com.dazzle.asklepios.repository.PointOfSaleConfigurationRepository;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleConfigurationDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PointOfSaleConfigurationService {
    private static final Logger LOG = LoggerFactory.getLogger(PointOfSaleConfigurationService.class);
    private static final String ENTITY_NAME =
            "pointOfSaleConfiguration";
    private final PointOfSaleConfigurationRepository pointOfSaleConfigurationRepository;
    private final PointOfSaleCheckInRepository pointOfSaleCheckInRepository;
    public PointOfSaleConfigurationDTO create(
            PointOfSaleConfigurationDTO request
    ) {

        PointOfSaleConfiguration entity =
                new PointOfSaleConfiguration();

        entity.setName(request.name());
        entity.setClientId(request.clientId());
        entity.setTerminalId(request.terminalId());
        entity.setTerminalSerialNo(request.terminalSerialNo());
        entity.setTerminalType(request.terminalType());
        entity.setCounterNumber(request.counterNumber());
        entity.setCashRegisterNo(request.cashRegisterNo());
        entity.setIsActive(request.isActive());

        return toDto(
                pointOfSaleConfigurationRepository.save(entity)
        );
    }

    public PointOfSaleConfigurationDTO update(
            Long id,
            PointOfSaleConfigurationDTO request
    ) {

        PointOfSaleConfiguration entity =
                pointOfSaleConfigurationRepository.findById(id)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "POS configuration not found",
                                        "pointOfSaleConfiguration",
                                        "notFound"
                                )
                        );

        entity.setName(request.name());
        entity.setClientId(request.clientId());
        entity.setTerminalId(request.terminalId());
        entity.setTerminalSerialNo(request.terminalSerialNo());
        entity.setTerminalType(request.terminalType());
        entity.setCounterNumber(request.counterNumber());
        entity.setCashRegisterNo(request.cashRegisterNo());
        entity.setIsActive(request.isActive());

        return toDto(
                pointOfSaleConfigurationRepository.save(entity)
        );
    }


    @Transactional
    public List<PointOfSaleConfigurationDTO> findAll(
            Boolean isActive
    ) {

        Specification<PointOfSaleConfiguration> specification =
                (root, query, cb) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    if (isActive != null) {

                        predicates.add(
                                cb.equal(
                                        root.get("isActive"),
                                        isActive
                                )
                        );
                    }

                    return cb.and(
                            predicates.toArray(
                                    new Predicate[0]
                            )
                    );
                };

        return pointOfSaleConfigurationRepository
                .findAll(specification)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PointOfSaleConfigurationDTO findOne(
            Long id
    ) {

        return pointOfSaleConfigurationRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "POS configuration not found",
                                "pointOfSaleConfiguration",
                                "notFound"
                        )
                );
    }

    private PointOfSaleConfigurationDTO toDto(
            PointOfSaleConfiguration entity
    ) {

        boolean occupied =
                pointOfSaleCheckInRepository
                        .existsByConfigurationIdAndActiveTrue(
                                entity.getId()
                        );

        return new PointOfSaleConfigurationDTO(
                entity.getId(),
                entity.getName(),
                entity.getClientId(),
                entity.getTerminalId(),
                entity.getTerminalSerialNo(),
                entity.getTerminalType(),
                entity.getCounterNumber(),
                entity.getCashRegisterNo(),
                entity.getIsActive(),
                occupied
        );
    }
    public PointOfSaleConfigurationDTO toggleActive(Long id) {

        LOG.debug("toggleActive for POS Configuration: id={}", id);

        PointOfSaleConfiguration entity =
                pointOfSaleConfigurationRepository.findById(id)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "PointOfSaleConfiguration not found: " + id,
                                ENTITY_NAME,
                                "notfound"
                        ));

        entity.setIsActive(Boolean.FALSE.equals(entity.getIsActive()));

        entity = pointOfSaleConfigurationRepository.save(entity);
        boolean occupied =
                pointOfSaleCheckInRepository
                        .existsByConfigurationIdAndActiveTrue(
                                entity.getId()
                        );
        return new PointOfSaleConfigurationDTO(
                entity.getId(),
                entity.getName(),
                entity.getClientId(),
                entity.getTerminalId(),
                entity.getTerminalSerialNo(),
                entity.getTerminalType(),
                entity.getCounterNumber(),
                entity.getCashRegisterNo(),
                entity.getIsActive(),
                occupied

        );
    }
}