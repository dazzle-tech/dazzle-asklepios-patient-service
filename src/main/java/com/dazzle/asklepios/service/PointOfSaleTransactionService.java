package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.NamiCloud.NamiCloudClient;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiPurchaseResponse;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiTransactionResponse;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PointOfSaleCheckIn;
import com.dazzle.asklepios.domain.PointOfSaleConfiguration;
import com.dazzle.asklepios.domain.PointOfSaleTransaction;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleTransactionType;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PointOfSaleCheckInRepository;
import com.dazzle.asklepios.repository.PointOfSaleTransactionRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.pointOfSale.CreatePointOfSaleTransactionDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleTransactionDTO;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleWebhookDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PointOfSaleTransactionService {
    private static final Logger LOG = LoggerFactory.getLogger(PointOfSaleTransactionService.class);

    private final PointOfSaleTransactionRepository pointOfSaleTransactionRepository;
    private final PointOfSaleCheckInRepository pointOfSaleCheckInRepository;
    private final PatientRepository patientRepository;

    private final NamiCloudClient namiCloudClient;

    public PointOfSaleTransactionDTO purchase(
            CreatePointOfSaleTransactionDTO request
    ) {

        log.info(
                "POS Purchase Started. PatientId={}, Amount={}, SourceType={}, SourceReferenceId={}",
                request.patientId(),
                request.amount(),
                request.sourceType(),
                request.sourceReferenceId()
        );

        String currentUserLogin =
                SecurityUtils.getCurrentUserLogin()
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Current user not found",
                                        "user",
                                        "notFound"
                                )
                        );

        log.info(
                "Current User Login={}",
                currentUserLogin
        );

        PointOfSaleCheckIn checkIn =
                pointOfSaleCheckInRepository
                        .findByUserLoginAndActiveTrue(
                                currentUserLogin
                        )
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "User is not checked in to any POS device",
                                        "pointOfSaleCheckIn",
                                        "notCheckedIn"
                                )
                        );

        PointOfSaleConfiguration configuration =
                checkIn.getConfiguration();

        log.info(
                "POS Device Selected. ConfigurationId={}, TerminalId={}, ClientId={}",
                configuration.getId(),
                configuration.getTerminalId(),
                configuration.getClientId()
        );

        Patient patient =
                patientRepository.findById(
                                request.patientId()
                        )
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Patient not found",
                                        "patient",
                                        "notFound"
                                )
                        );

        String orderId =
                generateOrderId();

        log.info(
                "Generated OrderId={}",
                orderId
        );

        PointOfSaleTransaction transaction =
                new PointOfSaleTransaction();

        transaction.setOrderId(orderId);
        transaction.setPatient(patient);
        transaction.setConfiguration(configuration);

        transaction.setSourceType(
                request.sourceType()
        );

        transaction.setSourceReferenceId(
                request.sourceReferenceId()
        );

        transaction.setAmount(
                request.amount()
        );

        transaction.setTransactionType(
                PointOfSaleTransactionType.PURCHASE
        );

        transaction.setTransactionStatus(
                PointOfSaleTransactionStatus.PENDING
        );

        transaction =
                pointOfSaleTransactionRepository.save(
                        transaction
                );

        log.info(
                "POS Transaction Saved. InternalId={}, Status={}",
                transaction.getId(),
                transaction.getTransactionStatus()
        );

        try {

            NamiPurchaseResponse response =
                    namiCloudClient.purchase(
                            configuration,
                            orderId,
                            request.amount()
                    );

            log.info(
                    "Nami Purchase Response. TransactionId={}, Status={}, StatusCode={}, Message={}",
                    response.transactionid(),
                    response.status(),
                    response.statusCode(),
                    response.statusMessage()
            );

            transaction.setExternalTransactionId(
                    response.transactionid()
            );

            transaction.setResponseCode(
                    String.valueOf(
                            response.statusCode()
                    )
            );

            transaction.setResponseMessage(
                    response.statusMessage()
            );

            transaction.setRawResponse(
                    response.toString()
            );

            boolean transactionCreated =
                    response.transactionid() != null
                            && !response.transactionid().isBlank();

            if (transactionCreated) {

                transaction.setTransactionStatus(
                        PointOfSaleTransactionStatus.PROCESSING
                );

                log.info(
                        "POS Transaction Processing. InternalId={}, ExternalTransactionId={}",
                        transaction.getId(),
                        transaction.getExternalTransactionId()
                );

            } else {

                transaction.setTransactionStatus(
                        PointOfSaleTransactionStatus.FAILED
                );

                log.warn(
                        "POS Transaction Failed. InternalId={}, No External Transaction Id Returned",
                        transaction.getId()
                );
            }

        } catch (Exception ex) {

            log.error(
                    "POS Purchase Exception. InternalId={}",
                    transaction.getId(),
                    ex
            );

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.FAILED
            );

            transaction.setResponseMessage(
                    ex.getMessage()
            );
        }

        transaction =
                pointOfSaleTransactionRepository.save(
                        transaction
                );

        log.info(
                "POS Transaction Updated. InternalId={}, Status={}, ExternalTransactionId={}",
                transaction.getId(),
                transaction.getTransactionStatus(),
                transaction.getExternalTransactionId()
        );

        return new PointOfSaleTransactionDTO(
                transaction.getId(),
                transaction.getPatient().getId(),
                transaction.getPatientPayment() != null
                        ? transaction.getPatientPayment().getId()
                        : null,
                transaction.getConfiguration().getId(),
                transaction.getSourceType(),
                transaction.getSourceReferenceId(),
                transaction.getOrderId(),
                transaction.getExternalTransactionId(),
                transaction.getTransactionType(),
                transaction.getTransactionStatus(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getResponseCode(),
                transaction.getResponseMessage(),
                transaction.getRrn(),
                transaction.getAuthCode(),
                transaction.getTerminalId(),
                transaction.getMerchantId(),
                transaction.getBatchNo(),
                transaction.getPaymentMethod(),
                transaction.getSchemeLabel(),
                transaction.getTransactionDate(),
                transaction.getWebhookReceived(),
                transaction.getStanNo(),
                transaction.getProductInfo(),
                transaction.getMerchantName(),
                transaction.getMerchantAddress(),
                transaction.getEcrTransactionReferenceNumber(),
                transaction.getApplicationVersion()
        );
    }

    public void processWebhook(
            PointOfSaleWebhookDTO webhook
    ) {

        log.info(
                "Webhook Received. TransactionId={}, ResponseCode={}, RRN={}",
                webhook.transactionId(),
                webhook.responseCode(),
                webhook.rrn()
        );

        PointOfSaleTransaction transaction =
                pointOfSaleTransactionRepository
                        .findByExternalTransactionId(
                                webhook.transactionId()
                        )
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Transaction not found",
                                        "pointOfSaleTransaction",
                                        "notFound"
                                )
                        );

        log.info(
                "Matching POS Transaction Found. InternalId={}, ExternalTransactionId={}",
                transaction.getId(),
                transaction.getExternalTransactionId()
        );

        transaction.setExternalTransactionId(
                webhook.transactionId()
        );

        transaction.setResponseCode(
                webhook.responseCode()
        );

        transaction.setResponseMessage(
                webhook.responseMessage()
        );

        transaction.setRrn(
                webhook.rrn()
        );

        transaction.setAuthCode(
                webhook.authCode()
        );

        transaction.setTerminalId(
                webhook.tid()
        );

        transaction.setMerchantId(
                webhook.mid()
        );

        transaction.setBatchNo(
                webhook.batchNo()
        );

        transaction.setWebhookReceived(
                Boolean.TRUE
        );

        if (isApproved(webhook.responseCode())) {

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.APPROVED
            );

            log.info(
                    "Webhook Approved. InternalId={}, RRN={}",
                    transaction.getId(),
                    transaction.getRrn()
            );

        } else {

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.DECLINED
            );

            log.warn(
                    "Webhook Declined. InternalId={}, ResponseCode={}",
                    transaction.getId(),
                    webhook.responseCode()
            );
        }

        pointOfSaleTransactionRepository.save(
                transaction
        );

        log.info(
                "Webhook Processing Completed. InternalId={}, FinalStatus={}",
                transaction.getId(),
                transaction.getTransactionStatus()
        );
    }
    private String generateOrderId() {

        return "ORDER" +
                Instant.now()
                        .toEpochMilli();
    }

    public PointOfSaleTransactionDTO refreshTransactionStatus(
            Long pointOfSaleTransactionId
    ) {

        log.info(
                "Refresh Transaction Status Started. InternalId={}",
                pointOfSaleTransactionId
        );

        PointOfSaleTransaction transaction =
                pointOfSaleTransactionRepository
                        .findById(pointOfSaleTransactionId)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Transaction not found",
                                        "pointOfSaleTransaction",
                                        "notFound"
                                )
                        );

        if (
                transaction.getExternalTransactionId() == null ||
                        transaction.getExternalTransactionId().isBlank()
        ) {

            throw new BadRequestAlertException(
                    "External transaction id is missing",
                    "pointOfSaleTransaction",
                    "externalTransactionIdMissing"
            );
        }

        log.info(
                "Calling Nami Transaction Status API. ExternalTransactionId={}",
                transaction.getExternalTransactionId()
        );

        NamiTransactionResponse response;

        try {

            response =
                    namiCloudClient.getTransactionResponse(
                            transaction.getExternalTransactionId()
                    );

        } catch (
                HttpClientErrorException.NotFound ex
        ) {

            log.info(
                    "Transaction still not available in Nami. InternalId={}, ExternalTransactionId={}",
                    transaction.getId(),
                    transaction.getExternalTransactionId()
            );

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.PROCESSING
            );

            transaction =
                    pointOfSaleTransactionRepository.save(
                            transaction
                    );

            return new PointOfSaleTransactionDTO(
                    transaction.getId(),
                    transaction.getPatient().getId(),
                    transaction.getPatientPayment() != null
                            ? transaction.getPatientPayment().getId()
                            : null,
                    transaction.getConfiguration().getId(),
                    transaction.getSourceType(),
                    transaction.getSourceReferenceId(),
                    transaction.getOrderId(),
                    transaction.getExternalTransactionId(),
                    transaction.getTransactionType(),
                    transaction.getTransactionStatus(),
                    transaction.getAmount(),
                    transaction.getCurrencyCode(),
                    transaction.getResponseCode(),
                    transaction.getResponseMessage(),
                    transaction.getRrn(),
                    transaction.getAuthCode(),
                    transaction.getTerminalId(),
                    transaction.getMerchantId(),
                    transaction.getBatchNo(),
                    transaction.getPaymentMethod(),
                    transaction.getSchemeLabel(),
                    transaction.getTransactionDate(),
                    transaction.getWebhookReceived(),
                    transaction.getStanNo(),
                    transaction.getProductInfo(),
                    transaction.getMerchantName(),
                    transaction.getMerchantAddress(),
                    transaction.getEcrTransactionReferenceNumber(),
                    transaction.getApplicationVersion()
            );
        }

        log.info(
                "Transaction Status Response Received. ResponseCode={}, RRN={}, TransactionId={}",
                response.responseCode(),
                response.rrn(),
                response.transactionId()
        );

        transaction.setResponseCode(
                response.responseCode()
        );

        transaction.setResponseMessage(
                response.responseMessage()
        );

        transaction.setRrn(
                response.rrn()
        );

        transaction.setAuthCode(
                response.authCode()
        );

        transaction.setTerminalId(
                response.tid()
        );

        transaction.setMerchantId(
                response.mid()
        );

        transaction.setBatchNo(
                response.batchNo()
        );

        transaction.setPaymentMethod(
                response.cardEntryMode()
        );

        transaction.setSchemeLabel(
                response.schemeLabel()
        );

        transaction.setStanNo(
                response.stanNo()
        );

        transaction.setProductInfo(
                response.productInfo()
        );

        transaction.setMerchantName(
                response.merchantName()
        );

        transaction.setMerchantAddress(
                response.merchantAddress()
        );

        transaction.setEcrTransactionReferenceNumber(
                response.ecrTransactionReferenceNumber()
        );

        transaction.setApplicationVersion(
                response.applicationVersion()
        );

        if (isApproved(response.responseCode())) {

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.APPROVED
            );

        } else if (
                response.responseCode() != null &&
                        !response.responseCode().isBlank()
        ) {

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.DECLINED
            );
        }

        transaction =
                pointOfSaleTransactionRepository.save(
                        transaction
                );

        log.info(
                "Transaction Status Updated. InternalId={}, FinalStatus={}, RRN={}",
                transaction.getId(),
                transaction.getTransactionStatus(),
                transaction.getRrn()
        );

        return new PointOfSaleTransactionDTO(
                transaction.getId(),
                transaction.getPatient().getId(),
                transaction.getPatientPayment() != null
                        ? transaction.getPatientPayment().getId()
                        : null,
                transaction.getConfiguration().getId(),
                transaction.getSourceType(),
                transaction.getSourceReferenceId(),
                transaction.getOrderId(),
                transaction.getExternalTransactionId(),
                transaction.getTransactionType(),
                transaction.getTransactionStatus(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getResponseCode(),
                transaction.getResponseMessage(),
                transaction.getRrn(),
                transaction.getAuthCode(),
                transaction.getTerminalId(),
                transaction.getMerchantId(),
                transaction.getBatchNo(),
                transaction.getPaymentMethod(),
                transaction.getSchemeLabel(),
                transaction.getTransactionDate(),
                transaction.getWebhookReceived(),
                transaction.getStanNo(),
                transaction.getProductInfo(),
                transaction.getMerchantName(),
                transaction.getMerchantAddress(),
                transaction.getEcrTransactionReferenceNumber(),
                transaction.getApplicationVersion()
        );
    }
    private boolean isApproved(
            String responseCode
    ) {
        return "000".equals(responseCode);
    }
}
