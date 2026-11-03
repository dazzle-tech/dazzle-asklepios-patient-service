package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.security.AuthoritiesConstants;
import com.dazzle.asklepios.service.DailyBriefingService;
import com.dazzle.asklepios.service.DailyBriefingService.BriefingRequest;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingCasesResponse;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingIssueResponse;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/patient/daily-briefing")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated() and !hasAuthority('" + AuthoritiesConstants.PATIENT + "')")
public class DailyBriefingController {

    private final DailyBriefingService dailyBriefingService;

    @GetMapping
    public ResponseEntity<DailyBriefingResponse> briefing(
            @RequestParam Long facilityId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long practitionerId,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer openEncounterHours,
            @RequestParam(required = false) Integer noShowRatePercent,
            @RequestParam(required = false) Integer waitingMinutes,
            @RequestParam(required = false) Integer turnaroundMinutes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occupancyAt
    ) {
        return ResponseEntity.ok(dailyBriefingService.briefing(new BriefingRequest(
                facilityId, departmentId, practitionerId, specialty, branchId, reportDate, language,
                openEncounterHours, noShowRatePercent, waitingMinutes, turnaroundMinutes, occupancyAt, null
        )));
    }

    @PostMapping("/issues")
    public ResponseEntity<DailyBriefingIssueResponse> issue(@RequestBody BriefingRequest request) {
        return ResponseEntity.ok(dailyBriefingService.issue(request));
    }

    @GetMapping("/issues/{id}")
    public ResponseEntity<DailyBriefingIssueResponse> stored(@PathVariable Long id) {
        return ResponseEntity.ok(dailyBriefingService.stored(id));
    }

    @GetMapping("/cases")
    public ResponseEntity<DailyBriefingCasesResponse> cases(
            @RequestParam String indicator,
            @RequestParam Long facilityId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long practitionerId,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(dailyBriefingService.cases(new BriefingRequest(
                facilityId, departmentId, practitionerId, specialty, branchId, reportDate, language,
                null, null, null, null, null, null
        ), indicator, limit));
    }
}
