package com.dazzle.asklepios.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorizes the patient-portal OTP endpoints with a shared secret.
 * These routes run before a patient JWT exists, so they are not checked against the bearer token.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PatientPortalSecretFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER = "X-Portal-Secret";

    private static final Logger LOG = LoggerFactory.getLogger(PatientPortalSecretFilter.class);

    private static final String REQUEST_OTP_PATH = "/api/patient/patient-portal/request-otp";
    private static final String VERIFY_OTP_PATH = "/api/patient/patient-portal/verify-login-otp";

    private final String portalSecret;

    public PatientPortalSecretFilter(@Value("${patient.portal.secret:}") String portalSecret) {
        this.portalSecret = portalSecret == null ? "" : portalSecret;
        if (this.portalSecret.isBlank()) {
            LOG.warn("patient.portal.secret is not set; patient portal OTP endpoints will reject every request");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isPortalOtpRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String provided = request.getHeader(SECRET_HEADER);
        if (!secretMatches(provided)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean secretMatches(String provided) {
        if (portalSecret.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
            sha256(provided),
            sha256(portalSecret)
        );
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean isPortalOtpRequest(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return REQUEST_OTP_PATH.equals(path) || VERIFY_OTP_PATH.equals(path);
    }
}
