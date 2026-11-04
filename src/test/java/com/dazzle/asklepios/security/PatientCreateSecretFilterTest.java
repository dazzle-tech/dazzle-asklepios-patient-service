package com.dazzle.asklepios.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PatientCreateSecretFilterTest {

    private static final String SECRET = "dev-patient-portal-secret";

    @Test
    void rejectsRequestWithoutSecret() throws Exception {
        PatientPortalSecretFilter filter = new PatientPortalSecretFilter(SECRET);
        MockHttpServletRequest request = externalCreateRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("filter chain should not continue");
        });

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsWrongSecret() throws Exception {
        PatientPortalSecretFilter filter = new PatientPortalSecretFilter(SECRET);
        MockHttpServletRequest request = externalCreateRequest();
        request.addHeader(PatientPortalSecretFilter.SECRET_HEADER, "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("filter chain should not continue");
        });

        assertEquals(401, response.getStatus());
    }

    @Test
    void allowsMatchingPortalSecret() throws Exception {
        PatientPortalSecretFilter filter = new PatientPortalSecretFilter(SECRET);
        MockHttpServletRequest request = externalCreateRequest();
        request.addHeader(PatientPortalSecretFilter.SECRET_HEADER, SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] continued = { false };

        filter.doFilter(request, response, (req, res) -> continued[0] = true);

        assertTrue(continued[0]);
    }

    @Test
    void ignoresTokenAuthenticatedCreate() throws Exception {
        PatientPortalSecretFilter filter = new PatientPortalSecretFilter(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/patient");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] continued = { false };

        filter.doFilter(request, response, (req, res) -> continued[0] = true);

        assertTrue(continued[0]);
    }

    private static MockHttpServletRequest externalCreateRequest() {
        return new MockHttpServletRequest("POST", "/api/patient/external");
    }
}
