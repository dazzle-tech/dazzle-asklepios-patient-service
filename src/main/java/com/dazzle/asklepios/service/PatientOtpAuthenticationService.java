package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.PatientLoginOtp;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientLoginOtpRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientPortal.PatientOtpRequestDTO;
import com.dazzle.asklepios.service.dto.patientPortal.PatientOtpVerifyDTO;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientOtpAuthenticationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientOtpAuthenticationService.class);

    private static final int OTP_EXPIRATION_MINUTES = 5;

    private static final int MAX_ATTEMPTS = 5;

    private final PatientDocumentRepository patientDocumentRepository;

    private final PatientLoginOtpRepository patientLoginOtpRepository;

    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();
    private final NotificationHelper notificationHelper;


    public String requestOtp(PatientOtpRequestDTO request) {

        if (request == null
                || request.primaryDocumentNumber() == null
                || request.primaryDocumentNumber().isBlank()) {

            throw new BadRequestAlertException(
                    "Primary document number is required",
                    "patient-portal",
                    "document.required"
            );
        }

        String primaryDocumentNumber =
                request.primaryDocumentNumber().trim();

        LOG.debug(
                "Requesting OTP for patient document number={}",
                primaryDocumentNumber
        );

        PatientDocument primaryDocument =
                patientDocumentRepository
                        .findByNumberAndIsPrimaryTrue(primaryDocumentNumber)
                        .orElseThrow(() -> new BadRequestAlertException(
                                "Invalid document number",
                                "patient-portal",
                                "invalid.credentials"
                        ));

        Patient patient = primaryDocument.getPatient();

        if (patient == null) {

            throw new BadRequestAlertException(
                    "Patient not found",
                    "patient-portal",
                    "invalid.credentials"
            );
        }


        if (patient.getPrimaryMobileNumber() == null
                || patient.getPrimaryMobileNumber().isBlank()) {

            throw new BadRequestAlertException(
                    "Patient does not have a registered mobile number",
                    "patient-portal",
                    "mobile.notfound"
            );
        }


        if (Boolean.FALSE.equals(patient.getReceiveSms())) {

            throw new BadRequestAlertException(
                    "Patient is not allowed to receive SMS",
                    "patient-portal",
                    "sms.disabled"
            );
        }


        invalidatePreviousOtp(patient.getId());

        String otp = generateOtp();
LOG.info(
                "Generated OTP for patientId={}: {}",
                patient.getId(),
                otp
        );
        String otpHash = passwordEncoder.encode(otp);

        Instant now = Instant.now();

        PatientLoginOtp patientLoginOtp =
                PatientLoginOtp.builder()
                        .patient(patient)
                        .otpHash(otpHash)
                        .expiresAt(
                                now.plus(
                                        OTP_EXPIRATION_MINUTES,
                                        ChronoUnit.MINUTES
                                )
                        )
                        .verifiedAt(null)
                        .attempts(0)
                        .createdAt(now)
                        .build();

        patientLoginOtpRepository.save(patientLoginOtp);

        sendOtpSms(patient, otp);

        LOG.info(
                "Patient OTP generated successfully. patientId={}",
                patient.getId()
        );
        return otp;
    }

    public Patient verifyOtp(PatientOtpVerifyDTO request) {

        if (request == null
                || request.primaryDocumentNumber() == null
                || request.primaryDocumentNumber().isBlank()) {

            throw new BadRequestAlertException(
                    "Primary document number is required",
                    "patient-portal",
                    "document.required"
            );
        }

        if (request.otp() == null || request.otp().isBlank()) {

            throw new BadRequestAlertException(
                    "OTP is required",
                    "patient-portal",
                    "otp.required"
            );
        }

        String primaryDocumentNumber =
                request.primaryDocumentNumber().trim();

        String otp = request.otp().trim();

        PatientDocument primaryDocument =
                patientDocumentRepository
                        .findByNumberAndIsPrimaryTrue(primaryDocumentNumber)
                        .orElseThrow(() -> new BadRequestAlertException(
                                "Invalid OTP",
                                "patient-portal",
                                "invalid.otp"
                        ));

        Patient patient = primaryDocument.getPatient();

        if (patient == null) {
            throw new BadRequestAlertException(
                    "Invalid OTP",
                    "patient-portal",
                    "invalid.otp"
            );
        }

        PatientLoginOtp patientLoginOtp =
                patientLoginOtpRepository
                        .findTopByPatientIdAndVerifiedAtIsNullOrderByCreatedAtDesc(
                                patient.getId()
                        )
                        .orElseThrow(() -> new BadRequestAlertException(
                                "No valid OTP found",
                                "patient-portal",
                                "otp.notfound"
                        ));

        Instant now = Instant.now();

        if (patientLoginOtp.getExpiresAt() == null
                || patientLoginOtp.getExpiresAt().isBefore(now)) {

            throw new BadRequestAlertException(
                    "OTP has expired",
                    "patient-portal",
                    "otp.expired"
            );
        }
//TODO: Uncomment the following block if you want to enforce maximum attempts for OTP verification
//        if (patientLoginOtp.getAttempts() >= MAX_ATTEMPTS) {
//
//            throw new BadRequestAlertException(
//                    "Maximum OTP attempts exceeded",
//                    "patient-portal",
//                    "otp.maxAttempts"
//            );
//        }

        boolean validOtp =
                passwordEncoder.matches(
                        otp,
                        patientLoginOtp.getOtpHash()
                );

        if (!validOtp) {

            patientLoginOtp.setAttempts(
                    patientLoginOtp.getAttempts() + 1
            );

            patientLoginOtpRepository.save(patientLoginOtp);

            LOG.warn(
                    "Invalid patient portal OTP. patientId={}, attempts={}",
                    patient.getId(),
                    patientLoginOtp.getAttempts()
            );

            throw new BadRequestAlertException(
                    "Invalid OTP",
                    "patient-portal",
                    "invalid.otp"
            );
        }

        patientLoginOtp.setVerifiedAt(now);

        patientLoginOtpRepository.save(patientLoginOtp);

        LOG.info(
                "Patient portal OTP verified successfully. patientId={}",
                patient.getId()
        );

        return patient;
    }

    private void invalidatePreviousOtp(Long patientId) {

        patientLoginOtpRepository
                .findTopByPatientIdAndVerifiedAtIsNullOrderByCreatedAtDesc(
                        patientId
                )
                .ifPresent(previousOtp -> {

                    Instant now = Instant.now();

                    if (previousOtp.getExpiresAt().isAfter(now)) {

                        previousOtp.setExpiresAt(now);

                        patientLoginOtpRepository.save(previousOtp);
                    }
                });
    }


    private String generateOtp() {

        int minimum = 100000;
        int maximum = 1000000;

        return String.valueOf(
                minimum + secureRandom.nextInt(
                        maximum - minimum
                )
        );
    }


    private void sendOtpSms(Patient patient, String otp) {
        try {
            String patientName = notificationHelper.getPatientName(patient);

            Map<String, Object> data = new LinkedHashMap<>();

            data.put("patient_name", patientName);
            data.put("otp", otp);
            data.put("patient_mobile", patient.getPrimaryMobileNumber());

            String login = SecurityUtils.getCurrentUserLogin().orElse(null);

            Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule =
                    notificationHelper.resolveRecipients(
                            null,
                            login,
                            patient.getCreatedBy(),
                            patient,
                            null,
                            false
                    );

            notificationHelper.sendNotification(
                    null,
                    NotificationCode.PATIENT_PORTAL_OTP,
                    recipientsByRule,
                    data,
                    "PATIENT",
                    patient.getId()
            );

            LOG.info(
                    "[NOTIFICATION] Patient portal OTP notification sent for patient id={}",
                    patient.getId()
            );

        } catch (Exception e) {
            LOG.warn(
                    "[NOTIFICATION] Failed to send patient portal OTP notification. patientId={}, error={}",
                    patient.getId(),
                    e.getMessage(),
                    e
            );
        }
    }
}