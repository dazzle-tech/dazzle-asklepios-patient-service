package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.DailyBriefingAccessLog;
import com.dazzle.asklepios.domain.DailyBriefingIssue;
import com.dazzle.asklepios.repository.DailyBriefingAccessLogRepository;
import com.dazzle.asklepios.repository.DailyBriefingIssueRepository;
import com.dazzle.asklepios.security.AuthoritiesConstants;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dailybriefing.BriefingContext;
import com.dazzle.asklepios.service.dailybriefing.BriefingScope;
import com.dazzle.asklepios.service.dailybriefing.BriefingWindow;
import com.dazzle.asklepios.service.dailybriefing.DailyBriefingAssembler;
import com.dazzle.asklepios.service.dailybriefing.DailyBriefingFacts;
import com.dazzle.asklepios.service.dailybriefing.DailyBriefingQueryGateway;
import com.dazzle.asklepios.service.dailybriefing.PractitionerLabel;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingCasesResponse;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingCasesResponse.DailyBriefingCase;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingIssueResponse;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Drill;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper.ZoneResolution;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.ConflictAlertException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DailyBriefingService {

    static final int MAX_SPECIALTY_LOOKUPS = 300;
    private static final String FALLBACK_ZONE = "Asia/Riyadh";
    private static final String ENTITY = "dailyBriefing";

    private final DailyBriefingQueryGateway queryGateway;
    private final FacilityHelper facilityHelper;
    private final PractitionerHelper practitionerHelper;
    private final DailyBriefingIssueRepository issueRepository;
    private final DailyBriefingAccessLogRepository accessLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DailyBriefingResponse briefing(BriefingRequest request) {
        Prepared prepared = prepare(request);
        if (prepared.notCalculatedReason() != null) {
            return DailyBriefingAssembler.notCalculated(prepared.context(), prepared.notCalculatedReason());
        }
        DailyBriefingFacts facts = queryGateway.load(prepared.scope(), prepared.window());
        return DailyBriefingAssembler.assemble(prepared.context(), facts, practitionerDirectory(facts));
    }

    @Transactional
    public DailyBriefingIssueResponse issue(BriefingRequest request) {
        String recipient = recipient();
        DailyBriefingResponse report = briefing(request);
        Prepared prepared = prepare(request);
        Optional<DailyBriefingIssue> existing = issueRepository.findActive(
                prepared.context().facilityId(),
                prepared.context().departmentId(),
                prepared.context().practitionerId(),
                prepared.context().specialty(),
                prepared.context().currentDay(),
                recipient,
                prepared.context().language()
        );
        boolean supersededPrevious = false;
        if (existing.isPresent()) {
            if (request.supersedeReason() == null || request.supersedeReason().isBlank()) {
                throw new ConflictAlertException(
                        "A briefing for this period, scope, language, and recipient is already stored. Supply supersedeReason to store another version.",
                        ENTITY,
                        "duplicate"
                );
            }
            DailyBriefingIssue previous = existing.orElseThrow();
            previous.setSuperseded(true);
            previous.setSupersedeReason(request.supersedeReason().trim());
            supersededPrevious = true;
        }
        DailyBriefingIssue issue = new DailyBriefingIssue();
        issue.setFacilityId(prepared.context().facilityId());
        issue.setDepartmentId(prepared.context().departmentId());
        issue.setPractitionerId(prepared.context().practitionerId());
        issue.setSpecialty(prepared.context().specialty());
        issue.setReportDate(prepared.context().currentDay());
        issue.setLanguage(prepared.context().language());
        issue.setRecipient(recipient);
        issue.setCreatedAt(Instant.now());
        issue.setStatus(report.issuance().status());
        issue.setErrors(String.join("; ", report.issuance().errors()));
        issue.setSuperseded(false);
        issue.setPayload(write(report));
        try {
            issue = issueRepository.saveAndFlush(issue);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The briefing could not be stored. Apply the gateway Liquibase changelog 2026110302_SA_daily_briefing and try again.");
        }
        return new DailyBriefingIssueResponse(issue.getId(), issue.getStatus(), issue.getCreatedAt(), recipient, supersededPrevious, report);
    }

    @Transactional(readOnly = true)
    public DailyBriefingIssueResponse stored(Long id) {
        DailyBriefingIssue issue = issueRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("Stored briefing was not found", ENTITY, "notfound"));
        assertFacility(issue.getFacilityId());
        String recipient = recipient();
        if (!issue.getRecipient().equals(recipient) && !SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            throw new AccessDeniedException("Stored briefings are available to the recipient who issued them.");
        }
        try {
            DailyBriefingResponse report = objectMapper.readValue(issue.getPayload(), DailyBriefingResponse.class);
            return new DailyBriefingIssueResponse(issue.getId(), issue.getStatus(), issue.getCreatedAt(), issue.getRecipient(), issue.isSuperseded(), report);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The stored briefing could not be read.");
        }
    }

    @Transactional
    public DailyBriefingCasesResponse cases(BriefingRequest request, String indicator, int limit) {
        Drill drill = parseDrill(indicator);
        int pageSize = Math.min(Math.max(limit, 1), 200);
        Prepared prepared = prepare(request);
        if (prepared.notCalculatedReason() != null) {
            throw new BadRequestAlertException(prepared.notCalculatedReason(), ENTITY, "notcalculated");
        }
        List<DailyBriefingCase> found = queryGateway.cases(drill, prepared.scope(), prepared.window(), pageSize + 1);
        boolean truncated = found.size() > pageSize;
        List<DailyBriefingCase> page = truncated ? found.subList(0, pageSize) : found;
        String recipient = recipient();
        DailyBriefingAccessLog audit = new DailyBriefingAccessLog();
        audit.setAccessedAt(Instant.now());
        audit.setAccessedBy(recipient);
        audit.setFacilityId(prepared.context().facilityId());
        audit.setDepartmentId(prepared.context().departmentId());
        audit.setPractitionerId(prepared.context().practitionerId());
        audit.setSpecialty(prepared.context().specialty());
        audit.setReportDate(prepared.context().currentDay());
        audit.setIndicator(drill.name());
        audit.setCaseCount(page.size());
        audit.setCaseIds(page.stream().map(row -> String.valueOf(row.caseId())).reduce((left, right) -> left + "," + right).orElse(""));
        try {
            accessLogRepository.saveAndFlush(audit);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Case details were not returned because the access log could not be written. Apply the gateway Liquibase changelog 2026110302_SA_daily_briefing.");
        }
        return new DailyBriefingCasesResponse(
                drill.name(),
                prepared.context().currentDay(),
                page.size(),
                truncated,
                "Case details are limited to internal identifiers. Patient names and clinical text are not included. This read was written to the access log.",
                page
        );
    }

    private Prepared prepare(BriefingRequest request) {
        if (request.facilityId() == null) {
            throw new BadRequestAlertException("facilityId is required", ENTITY, "facility.required");
        }

        String language = request.language() == null || request.language().isBlank() ? "en" : request.language().trim().toLowerCase();
        if (!language.equals("en") && !language.equals("ar")) {
            throw new BadRequestAlertException("language must be en or ar", ENTITY, "language.invalid");
        }
        int openHours = bounded(request.openEncounterHours(), 24, 1, 168, "openEncounterHours");
        int noShow = bounded(request.noShowRatePercent(), 15, 1, 100, "noShowRatePercent");
        int waiting = bounded(request.waitingMinutes(), 30, 1, 1440, "waitingMinutes");
        int turnaround = bounded(request.turnaroundMinutes(), 240, 1, 10080, "turnaroundMinutes");
        assertFacility(request.facilityId());
        FacilityDTO facility = facilityHelper.getFacility(request.facilityId());
        ZoneResolution zone = facilityHelper.resolveZone(request.facilityId(), FALLBACK_ZONE);
        LocalDate today = LocalDate.now(zone.zoneId());
        LocalDate currentDay = request.reportDate() == null ? today : request.reportDate();
        if (currentDay.isAfter(today)) {
            throw new BadRequestAlertException("reportDate cannot be in the future for the facility time zone", ENTITY, "reportDate.future");
        }
        Instant refreshedAt = Instant.now();
        if (request.occupancyAt() != null && request.occupancyAt().isAfter(refreshedAt)) {
            throw new BadRequestAlertException("occupancyAt cannot be in the future", ENTITY, "occupancyAt.future");
        }
        String specialty = request.specialty() == null || request.specialty().isBlank() ? null : request.specialty().trim();
        SpecialtyResolution resolution = resolveSpecialty(request.facilityId(), request.departmentId(), request.practitionerId(), specialty, currentDay, zone.zoneId());
        LocalDate previousDay = currentDay.minusDays(1);
        LocalDate comparisonDay = previousDay.minusWeeks(1);
        Instant currentStart = currentDay.atStartOfDay(zone.zoneId()).toInstant();
        Instant currentEnd = currentDay.plusDays(1).atStartOfDay(zone.zoneId()).toInstant();
        Instant previousStart = previousDay.atStartOfDay(zone.zoneId()).toInstant();
        Instant previousEnd = currentStart;
        Instant comparisonStart = comparisonDay.atStartOfDay(zone.zoneId()).toInstant();
        Instant comparisonEnd = comparisonDay.plusDays(1).atStartOfDay(zone.zoneId()).toInstant();
        String permission = SecurityUtils.getCurrentUserFacility().isPresent()
                ? "Facility filter matches the signed-in facility. Department, specialty, and physician filters are applied when supplied. This service does not store a finer permission matrix for those dimensions."
                : "The access token has no facility claim. The requested facility was used. Department, specialty, and physician filters are applied when supplied.";
        if ("ar".equals(language)) {
            permission = SecurityUtils.getCurrentUserFacility().isPresent()
                    ? "مرشح المنشأة يطابق منشأة المستخدم. تُطبق مرشحات القسم والتخصص والطبيب عند توفيرها. لا تحتفظ هذه الخدمة بمصفوفة صلاحيات أدق لهذه الأبعاد."
                    : "رمز الدخول لا يحتوي مطالبة منشأة. استُخدمت المنشأة المطلوبة. تُطبق مرشحات القسم والتخصص والطبيب عند توفيرها.";
        }
        BriefingContext context = new BriefingContext(
                language,
                facility.name() == null ? "Facility " + facility.id() : facility.name(),
                request.facilityId(),
                request.departmentId(),
                resolution.matchNone() ? request.practitionerId() : request.practitionerId(),
                specialty,
                currentDay,
                zone.zoneId().getId(),
                zone.source(),
                currentDay,
                previousDay,
                comparisonDay,
                refreshedAt,
                request.occupancyAt() == null ? refreshedAt : request.occupancyAt(),
                refreshedAt.isBefore(currentEnd),
                zone.zoneId(),
                openHours,
                noShow,
                waiting,
                turnaround,
                permission
        );
        BriefingScope scope = new BriefingScope(
                request.facilityId(),
                request.departmentId(),
                resolution.matchNone() ? null : request.practitionerId(),
                resolution.practitionerIds(),
                resolution.matchNone()
        );
        BriefingWindow window = new BriefingWindow(
                zone.zoneId(), currentDay, previousDay, comparisonDay,
                currentStart, currentEnd, previousStart, previousEnd, comparisonStart, comparisonEnd,
                refreshedAt, context.occupancyAt(), turnaround, openHours
        );
        return new Prepared(context, scope, window, resolution.notCalculatedReason());
    }

    private SpecialtyResolution resolveSpecialty(Long facilityId, Long departmentId, Long practitionerId, String specialty, LocalDate currentDay, ZoneId zone) {
        if (specialty == null) {
            return new SpecialtyResolution(null, false, null);
        }
        if (practitionerId != null) {
            PractitionerDTO practitioner = readPractitioner(practitionerId);
            if (practitioner == null) {
                return new SpecialtyResolution(null, false, "SPECIALTY_LOOKUP_FAILED");
            }
            boolean matches = specialty.equalsIgnoreCase(practitioner.specialty() == null ? "" : practitioner.specialty().trim());
            return new SpecialtyResolution(null, !matches, null);
        }
        LocalDate comparisonDay = currentDay.minusDays(1).minusWeeks(1);
        Instant from = comparisonDay.atStartOfDay(zone).toInstant();
        Instant to = currentDay.plusDays(1).atStartOfDay(zone).toInstant();
        Set<Long> candidates = queryGateway.practitionerIds(facilityId, departmentId, comparisonDay, currentDay, from, to);
        if (candidates.size() > MAX_SPECIALTY_LOOKUPS) {
            return new SpecialtyResolution(null, false, "SPECIALTY_LOOKUP_LIMIT");
        }
        List<Long> matched = new ArrayList<>();
        for (Long id : candidates) {
            PractitionerDTO practitioner = readPractitioner(id);
            if (practitioner == null) {
                return new SpecialtyResolution(null, false, "SPECIALTY_LOOKUP_FAILED");
            }
            if (specialty.equalsIgnoreCase(practitioner.specialty() == null ? "" : practitioner.specialty().trim())) {
                matched.add(id);
            }
        }
        if (matched.isEmpty()) {
            return new SpecialtyResolution(null, true, null);
        }
        return new SpecialtyResolution(matched, false, null);
    }

    private Map<Long, PractitionerLabel> practitionerDirectory(DailyBriefingFacts facts) {
        Map<Long, PractitionerLabel> directory = new LinkedHashMap<>();
        if (facts.previousEncounters() == null) {
            return directory;
        }
        for (DailyBriefingFacts.EncounterRow row : facts.previousEncounters()) {
            Long id = row.practitionerId();
            if (id == null || directory.containsKey(id)) {
                continue;
            }
            PractitionerDTO practitioner = readPractitioner(id);
            if (practitioner == null) {
                directory.put(id, new PractitionerLabel(false, null, null));
                continue;
            }
            directory.put(id, new PractitionerLabel(true, displayName(practitioner), blankToNull(practitioner.specialty())));
        }
        return directory;
    }

    private PractitionerDTO readPractitioner(Long id) {
        try {
            return practitionerHelper.getPractitioner(id);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String displayName(PractitionerDTO practitioner) {
        String first = practitioner.firstName() == null ? "" : practitioner.firstName().trim();
        String last = practitioner.lastName() == null ? "" : practitioner.lastName().trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void assertFacility(Long facilityId) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return;
        }
        Optional<Long> tokenFacility = SecurityUtils.getCurrentUserFacility();
        if (tokenFacility.isPresent() && !tokenFacility.orElseThrow().equals(facilityId)) {
            throw new AccessDeniedException("The signed-in facility does not match facilityId.");
        }
    }

    private String recipient() {
        String login = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AccessDeniedException("A signed-in user is required."));
        return login.length() <= 100 ? login : login.substring(0, 100);
    }

    private Drill parseDrill(String indicator) {
        try {
            return Drill.valueOf(indicator);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadRequestAlertException("Unknown case indicator", ENTITY, "indicator.invalid");
        }
    }

    private int bounded(Integer value, int fallback, int min, int max, String name) {
        int resolved = value == null ? fallback : value;
        if (resolved < min || resolved > max) {
            throw new BadRequestAlertException(name + " must be between " + min + " and " + max, ENTITY, name + ".invalid");
        }
        return resolved;
    }

    private String write(DailyBriefingResponse report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The briefing could not be serialized.");
        }
    }

    public record BriefingRequest(
            Long facilityId,
            Long departmentId,
            Long practitionerId,
            String specialty,
            LocalDate reportDate,
            String language,
            Integer openEncounterHours,
            Integer noShowRatePercent,
            Integer waitingMinutes,
            Integer turnaroundMinutes,
            Instant occupancyAt,
            String supersedeReason
    ) {
    }

    private record Prepared(BriefingContext context, BriefingScope scope, BriefingWindow window, String notCalculatedReason) {
    }

    private record SpecialtyResolution(List<Long> practitionerIds, boolean matchNone, String notCalculatedReason) {
    }
}
