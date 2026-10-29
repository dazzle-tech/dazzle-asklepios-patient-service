package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientPortal.PatientOtpRequestDTO;
import com.dazzle.asklepios.service.dto.patientPortal.PatientOtpVerifyDTO;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.survey.SurveyOtpVerifiedVM;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP of the web patient satisfaction survey. The app signs in with the patient portal endpoints
 * (they need the portal secret); the web page cannot keep that secret, so it calls these instead.
 * The OTP itself is the patient portal one (PatientOtpAuthenticationService): same table, same SMS.
 * Without the secret anyone can call these, so requests are limited per ID number.
 * Like the patient portal request-otp, the OTP is returned in the response for now (testing).
 */
@Service
@Slf4j
public class SurveyOtpService {

    private static final String ENTITY_NAME = "survey";
    private static final long TOKEN_EXPIRATION_MINUTES = 60;

    // In-memory limits per ID number (not per IP: many patients can share one hospital or mobile network IP)
    private static final Limit OTP_PER_DOCUMENT_MINUTE = new Limit(1, 60);
    private static final Limit OTP_PER_DOCUMENT_HOUR = new Limit(5, 3600);
    private static final Limit VERIFY_PER_DOCUMENT = new Limit(5, 300);

    private final PatientOtpAuthenticationService portalOtpService;
    private final PatientRepository patientRepository;
    private final NotificationHelper notificationHelper;
    private final byte[] tokenKey;
    private final Map<String, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

    public SurveyOtpService(
            PatientOtpAuthenticationService portalOtpService,
            PatientRepository patientRepository,
            NotificationHelper notificationHelper,
            @Value("${patient.security.authentication.jwt.base64-secret}") String jwtKey
    ) {
        this.portalOtpService = portalOtpService;
        this.patientRepository = patientRepository;
        this.notificationHelper = notificationHelper;
        // Own key derived from the service secret: a survey token can never pass as a login JWT
        this.tokenKey = hmac(com.nimbusds.jose.util.Base64.from(jwtKey).decode(), "patient-satisfaction-survey");
    }

    public String requestOtp(PatientOtpRequestDTO request) {
        String document = documentKey(request.primaryDocumentNumber());
        checkLimit(OTP_PER_DOCUMENT_MINUTE, "otp-minute:" + document);
        checkLimit(OTP_PER_DOCUMENT_HOUR, "otp-hour:" + document);

        String otp = portalOtpService.requestOtp(request);

        // Only an OTP that was sent counts: a wrong ID number or a patient without a mobile does not
        record("otp-minute:" + document);
        record("otp-hour:" + document);
        return otp;
    }

    public SurveyOtpVerifiedVM verifyOtp(PatientOtpVerifyDTO request) {
        // Few wrong OTPs per document: the portal service does not limit them yet
        String verifyKey = "verify:" + documentKey(request.primaryDocumentNumber());
        checkLimit(VERIFY_PER_DOCUMENT, verifyKey);

        Long patientId;
        try {
            patientId = portalOtpService.verifyOtp(request);
        } catch (BadRequestAlertException e) {
            record(verifyKey);
            throw e;
        }

        Instant expiresAt = Instant.now().plus(TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        String name = patientRepository.findById(patientId)
                .map(notificationHelper::getPatientName)
                .filter(value -> !value.isBlank())
                .map(String::trim)
                .orElse(null);
        return new SurveyOtpVerifiedVM(name, createToken(patientId, expiresAt), expiresAt);
    }

    /** The patient the survey token was given to; fails when the token is changed or expired */
    public Long patientIdFromToken(String token) {
        String[] parts = token == null ? new String[0] : token.trim().split("\\.");
        if (parts.length != 2) {
            throw invalidToken();
        }

        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(parts[0]);
        } catch (IllegalArgumentException e) {
            throw invalidToken();
        }
        if (!MessageDigest.isEqual(hmac(tokenKey, payload), decodeOrEmpty(parts[1]))) {
            throw invalidToken();
        }

        String[] fields = new String(payload, StandardCharsets.UTF_8).split(":");
        try {
            Long patientId = Long.valueOf(fields[0]);
            if (Instant.ofEpochSecond(Long.parseLong(fields[1])).isBefore(Instant.now())) {
                throw new BadRequestAlertException(
                        "Your verification expired. Please verify again", ENTITY_NAME, "token.expired");
            }
            if (!patientRepository.existsById(patientId)) {
                throw invalidToken();
            }
            return patientId;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw invalidToken();
        }
    }

    private String createToken(Long patientId, Instant expiresAt) {
        byte[] payload = (patientId + ":" + expiresAt.getEpochSecond()).getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload) + "." + encoder.encodeToString(hmac(tokenKey, payload));
    }

    /** Fails with 429 when the key already used all the requests of the limit's window; records nothing */
    private void checkLimit(Limit limit, String key) {
        long windowStart = Instant.now().getEpochSecond() - limit.windowSeconds();
        Deque<Long> times = requestTimes.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= windowStart) {
                times.pollFirst();
            }
            if (times.size() >= limit.maxRequests()) {
                log.warn("Survey OTP rate-limited. key={}", key.replaceAll("[^a-zA-Z0-9.:_-]", ""));
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
            }
        }
    }

    private void record(String key) {
        Deque<Long> times = requestTimes.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(Instant.now().getEpochSecond());
        }
    }

    private static String documentKey(String primaryDocumentNumber) {
        return primaryDocumentNumber == null ? "" : primaryDocumentNumber.trim();
    }

    private static byte[] hmac(byte[] key, String data) {
        return hmac(key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static byte[] decodeOrEmpty(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static BadRequestAlertException invalidToken() {
        return new BadRequestAlertException("Invalid verification", ENTITY_NAME, "token.invalid");
    }

    private record Limit(int maxRequests, long windowSeconds) {
    }
}
