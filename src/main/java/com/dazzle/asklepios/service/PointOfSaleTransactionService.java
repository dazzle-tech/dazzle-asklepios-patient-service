package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.NamiCloud.NamiCloudClient;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiPurchaseResponse;
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
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PointOfSaleTransactionService {

    private final PointOfSaleTransactionRepository pointOfSaleTransactionRepository;
    private final PointOfSaleCheckInRepository pointOfSaleCheckInRepository;
    private final PatientRepository patientRepository;

    private final NamiCloudClient namiCloudClient;

    public PointOfSaleTransactionDTO purchase(
            CreatePointOfSaleTransactionDTO request
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

        PointOfSaleCheckIn checkIn =
                pointOfSaleCheckInRepository
                        .findByUserLoginAndActiveTrue(currentUserLogin)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "User is not checked in to any POS device",
                                        "pointOfSaleCheckIn",
                                        "notCheckedIn"
                                )
                        );

        PointOfSaleConfiguration configuration =
                checkIn.getConfiguration();

        Patient patient =
                patientRepository.findById(request.patientId())
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Patient not found",
                                        "patient",
                                        "notFound"
                                )
                        );

        String orderId = generateOrderId();

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

        NamiPurchaseResponse response =
                namiCloudClient.purchase(
                        configuration,
                        orderId,
                        request.amount()
                );

        transaction.setTransactionStatus(
                PointOfSaleTransactionStatus.PROCESSING
        );
        transaction.setExternalTransactionId(
                response.transactionid()
        );
        transaction.setRawResponse(
                response.toString()
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
                transaction.getExternalTransactionCode(),
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
                transaction.getExternalTransactionId()
        );
    }

    public void processWebhook(
            PointOfSaleWebhookDTO webhook
    ) {

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

        transaction.setExternalTransactionCode(
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

        if ("000".equals(webhook.responseCode())) {

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.APPROVED
            );

//            PatientPayments payment =
//                    createPatientPayment(
//                            transaction
//                    );
//            transaction.setPatientPayment(
//                    payment
//            );

        } else {

            transaction.setTransactionStatus(
                    PointOfSaleTransactionStatus.DECLINED
            );
        }

        pointOfSaleTransactionRepository.save(
                transaction
        );
    }

    private String generateOrderId() {

        return "POS-" + Instant.now().toEpochMilli();
    }
}
