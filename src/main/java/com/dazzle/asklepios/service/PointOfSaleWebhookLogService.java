package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PointOfSaleTransaction;
import com.dazzle.asklepios.domain.PointOfSaleWebhookLog;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleSourceType;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PointOfSaleWebhookLogRepository;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookLogDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookLogFilterDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PointOfSaleWebhookLogService {
    private static final Logger LOG = LoggerFactory.getLogger(PointOfSaleWebhookLogService.class);

    private final PointOfSaleWebhookLogRepository
            pointOfSaleWebhookLogRepository;

    private final PatientEncounterRepository
            patientEncounterRepository;

    private final ObjectMapper objectMapper;

    public void create(
            PointOfSaleWebhookDTO webhook
    ) {
        create(null, webhook);
    }

    public void create(
            PointOfSaleTransaction transaction,
            PointOfSaleWebhookDTO webhook
    ) {

        PointOfSaleWebhookLog entity =
                new PointOfSaleWebhookLog();

        entity.setTransaction(
                transaction
        );

        entity.setExternalTransactionId(
                webhook.transactionId()
        );

        entity.setOrderId(
                transaction != null
                        ? transaction.getOrderId()
                        : null
        );

        entity.setResponseCode(
                webhook.responseCode()
        );

        entity.setResponseMessage(
                webhook.responseMessage()
        );

        entity.setTransactionStatus(
                transaction != null
                        ? transaction.getTransactionStatus().name()
                        : null
        );

        entity.setRrn(
                webhook.rrn()
        );

        entity.setAuthCode(
                webhook.authCode()
        );

        entity.setTerminalId(
                webhook.tid()
        );

        entity.setMerchantId(
                webhook.mid()
        );

        entity.setBatchNo(
                webhook.batchNo()
        );

        entity.setStanNo(
                webhook.stanNo()
        );

        entity.setSchemeLabel(
                webhook.schemeLabel()
        );

        entity.setProductInfo(
                webhook.productInfo()
        );

        entity.setMerchantName(
                webhook.merchantName()
        );

        entity.setMerchantAddress(
                webhook.merchantAddress()
        );

        entity.setApplicationVersion(
                webhook.applicationVersion()
        );

        entity.setEcrTransactionReferenceNumber(
                webhook.ecrTransactionReferenceNumber()
        );

        entity.setProcessed(
                Boolean.TRUE
        );

        entity.setProcessingStatus(
                "SUCCESS"
        );

        try {

            entity.setRawPayload(
                    objectMapper.writeValueAsString(
                            webhook
                    )
            );

        } catch (
                JsonProcessingException ex
        ) {

            LOG.error(
                    "Failed to serialize webhook payload",
                    ex
            );

            entity.setProcessingStatus(
                    "FAILED"
            );

            entity.setProcessingError(
                    ex.getMessage()
            );

            entity.setRawPayload(
                    webhook.toString()
            );
        }

        pointOfSaleWebhookLogRepository.save(
                entity
        );

        LOG.info(
                "Webhook Log Saved. TransactionId={}, ResponseCode={}, OrderId={}",
                webhook.transactionId(),
                webhook.responseCode(),
                entity.getOrderId()
        );
    }

    @Transactional
    public Page<PointOfSaleWebhookLogDTO> findAll(
            PointOfSaleWebhookLogFilterDTO filter,
            Pageable pageable
    ) {

        Specification<PointOfSaleWebhookLog> specification =
                (root, query, cb) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    if (
                            StringUtils.hasText(
                                    filter.externalTransactionId()
                            )
                    ) {

                        predicates.add(
                                cb.like(
                                        cb.lower(
                                                root.get(
                                                        "externalTransactionId"
                                                )
                                        ),
                                        "%" +
                                                filter.externalTransactionId()
                                                        .toLowerCase()
                                                + "%"
                                )
                        );
                    }

                    if (
                            StringUtils.hasText(
                                    filter.orderId()
                            )
                    ) {

                        predicates.add(
                                cb.like(
                                        cb.lower(
                                                root.get(
                                                        "orderId"
                                                )
                                        ),
                                        "%"
                                                + filter.orderId()
                                                .toLowerCase()
                                                + "%"
                                )
                        );
                    }

                    if (
                            StringUtils.hasText(
                                    filter.responseCode()
                            )
                    ) {

                        predicates.add(
                                cb.equal(
                                        root.get(
                                                "responseCode"
                                        ),
                                        filter.responseCode()
                                )
                        );
                    }

                    if (
                            StringUtils.hasText(
                                    filter.encounterNumber()
                            )
                    ) {

                        List<Long> matchingEncounterIds =
                                patientEncounterRepository
                                        .findByEncounterNumberContainingIgnoreCase(
                                                filter.encounterNumber()
                                        )
                                        .stream()
                                        .map(
                                                encounter -> encounter.getId()
                                        )
                                        .toList();

                        if (matchingEncounterIds.isEmpty()) {

                            predicates.add(
                                    cb.disjunction()
                            );

                        } else {

                            Join<PointOfSaleWebhookLog, PointOfSaleTransaction>
                                    transactionJoin =
                                    root.join("transaction");

                            predicates.add(
                                    cb.and(
                                            cb.equal(
                                                    transactionJoin.get(
                                                            "sourceType"
                                                    ),
                                                    PointOfSaleSourceType.ENCOUNTER
                                            ),
                                            transactionJoin
                                                    .get("sourceReferenceId")
                                                    .in(
                                                            matchingEncounterIds
                                                    )
                                    )
                            );
                        }
                    }

                    if (
                            StringUtils.hasText(
                                    filter.processingStatus()
                            )
                    ) {

                        predicates.add(
                                cb.equal(
                                        root.get(
                                                "processingStatus"
                                        ),
                                        filter.processingStatus()
                                )
                        );
                    }

                    if (
                            filter.fromDate() != null
                    ) {

                        predicates.add(
                                cb.greaterThanOrEqualTo(
                                        root.get(
                                                "createdDate"
                                        ),
                                        filter.fromDate()
                                )
                        );
                    }

                    if (
                            filter.toDate() != null
                    ) {

                        predicates.add(
                                cb.lessThanOrEqualTo(
                                        root.get(
                                                "createdDate"
                                        ),
                                        filter.toDate()
                                )
                        );
                    }

                    query.orderBy(
                            cb.desc(
                                    root.get(
                                            "createdDate"
                                    )
                            )
                    );

                    return cb.and(
                            predicates.toArray(
                                    new Predicate[0]
                            )
                    );
                };

        Page<PointOfSaleWebhookLog> page =
                pointOfSaleWebhookLogRepository
                        .findAll(
                                specification,
                                pageable
                        );

        Set<Long> encounterIds =
                page.getContent()
                        .stream()
                        .map(this::resolveEncounterId)
                        .filter(Objects::nonNull)
                        .collect(
                                Collectors.toSet()
                        );

        Map<Long, String> encounterNumberById =
                encounterIds.isEmpty()
                        ? Map.of()
                        : patientEncounterRepository
                                .findAllById(encounterIds)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                PatientEncounter::getId,
                                                PatientEncounter::getEncounterNumber
                                        )
                                );

        return page.map(
                entity -> toDto(
                        entity,
                        encounterNumberById
                )
        );
    }

    private Long resolveEncounterId(
            PointOfSaleWebhookLog entity
    ) {

        PointOfSaleTransaction transaction =
                entity.getTransaction();

        return transaction != null &&
                transaction.getSourceType() ==
                        PointOfSaleSourceType.ENCOUNTER
                ? transaction.getSourceReferenceId()
                : null;
    }

    private PointOfSaleWebhookLogDTO toDto(
            PointOfSaleWebhookLog entity,
            Map<Long, String> encounterNumberById
    ) {

        PointOfSaleTransaction transaction =
                entity.getTransaction();

        Long encounterId =
                resolveEncounterId(entity);

        String encounterNumber =
                encounterId != null
                        ? encounterNumberById.get(encounterId)
                        : null;

        return new PointOfSaleWebhookLogDTO(

                entity.getId(),

                transaction != null
                        ? transaction.getId()
                        : null,

                transaction != null && transaction.getSourceType() != null
                        ? transaction.getSourceType().name()
                        : null,

                transaction != null && transaction.getPatient() != null
                        ? transaction.getPatient().getId()
                        : null,

                encounterId,

                encounterNumber,

                entity.getOrderId(),

                entity.getExternalTransactionId(),

                entity.getResponseCode(),

                entity.getResponseMessage(),

                entity.getTransactionStatus(),

                entity.getRrn(),

                entity.getAuthCode(),

                entity.getTerminalId(),

                entity.getMerchantId(),

                entity.getProcessingStatus(),

                entity.getProcessed(),

                entity.getCreatedDate()
        );
    }
}