package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.FacilityClient;
import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;

@Service
public class FacilityHelper {

    // the setup-service `TimeZone` enum stores Java-constant-style names (e.g. "ASIA_RIYADH"),
    // not real IANA zone identifiers - ZoneId.of("ASIA_RIYADH") throws, so each known value is
    // mapped to its real IANA id here rather than duplicating this mapping in every caller.
    private static final Map<String, String> ENUM_NAME_TO_IANA_ZONE = Map.of(
            "UTC", "UTC",
            "EUROPE_BERLIN", "Europe/Berlin",
            "ASIA_AMMAN", "Asia/Amman",
            "ASIA_RIYADH", "Asia/Riyadh",
            "ASIA_DUBAI", "Asia/Dubai",
            "AMERICA_NEW_YORK", "America/New_York"
    );

    private final FacilityClient facilityClient;
    public FacilityHelper(final FacilityClient facilityClient) {
        this.facilityClient = facilityClient;
    }

    /**
     * Resolves the real-world {@link ZoneId} for a facility, falling back to
     * {@code fallbackZone} when the facility has no time zone configured or its
     * value can't be resolved to a valid zone.
     */
    public ZoneId getFacilityZoneId(Long facilityId, String fallbackZone) {
        String rawTimeZone = facilityId != null ? getFacility(facilityId).timeZone() : null;

        if (rawTimeZone == null || rawTimeZone.isBlank()) {
            return ZoneId.of(fallbackZone);
        }

        String ianaZone = ENUM_NAME_TO_IANA_ZONE.getOrDefault(rawTimeZone, rawTimeZone);

        try {
            return ZoneId.of(ianaZone);
        } catch (DateTimeException ex) {
            return ZoneId.of(fallbackZone);
        }
    }

    public void validateFacilityExists(Long facilityId) {
        try {
            facilityClient.existsFacility(facilityId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Facility not found: " + facilityId,
                    "facility",
                    "notfound"
            );
        }
    }

    public FacilityDTO getFacility(Long facilityId) {
        try {
            return facilityClient.getFacility(facilityId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Facility not found: " + facilityId,
                    "facility",
                    "notfound"
            );
        }
    }
}
