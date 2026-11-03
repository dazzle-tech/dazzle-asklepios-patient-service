package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingCriteria;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingSyncResponse;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.ClaimTrackingSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Refreshes the registry's open claims from Waseel before a search, using Waseel's claim search by payer and period.
 * No call is made when nothing matching the search is still waiting for a result.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimTrackingSyncService {

    /** Waseel rejects a claim search longer than 10 days (both ends included). */
    private static final int MAX_SEARCH_DAYS = 10;

    /** A day of margin before each submission date so times near midnight are not missed. */
    private static final int MARGIN_DAYS = 1;

    private final ClaimRequestRepository claimRequestRepository;
    private final WaseelClaimService waseelClaimService;
    private final ClaimStatusRefreshService claimStatusRefreshService;

    public ClaimTrackingSyncResponse sync(ClaimTrackingCriteria criteria) {
        String payerNphiesId = criteria.payerNphiesId() == null ? null : criteria.payerNphiesId().trim();
        if (payerNphiesId == null || payerNphiesId.isEmpty()) {
            return new ClaimTrackingSyncResponse(0, 0);
        }

        List<ClaimRequest> openClaims = claimRequestRepository.findAll(
                        ClaimTrackingSpecification.of(criteria).and(ClaimTrackingSpecification.awaitingWaseelResult())
                ).stream()
                .filter(claim -> claim.getSubmittedAt() != null)
                .sorted(Comparator.comparing(ClaimRequest::getSubmittedAt))
                .toList();
        if (openClaims.isEmpty()) {
            return new ClaimTrackingSyncResponse(0, 0);
        }

        Map<Long, String> provClaimNoById = openClaims.stream()
                .collect(Collectors.toMap(ClaimRequest::getId, ClaimRequest::getProvClaimNo));

        int updated = 0;
        int failedWindows = 0;
        List<SearchWindow> windows = searchWindows(openClaims);
        for (SearchWindow window : windows) {
            try {
                JsonNode results = waseelClaimService.searchClaims(payerNphiesId, window.from(), window.to());
                updated += claimStatusRefreshService.applySearchResults(provClaimNoById, results);
            } catch (RestClientException ex) {
                failedWindows++;
                log.warn("[CLAIM_SYNC] Claim search failed payerNphiesId={} from={} to={}",
                        payerNphiesId, window.from(), window.to(), ex);
            }
        }
        if (failedWindows == windows.size()) {
            throw new IllegalStateException("Waseel claim search failed for every period");
        }

        log.info("[CLAIM_SYNC] payerNphiesId={} checked={} windows={} failedWindows={} updated={}",
                payerNphiesId, openClaims.size(), windows.size(), failedWindows, updated);
        return new ClaimTrackingSyncResponse(openClaims.size(), updated);
    }

    /**
     * Covers the claims' submission dates with as few searches of at most {@link #MAX_SEARCH_DAYS} days as possible.
     * Days without open claims are never searched.
     */
    private List<SearchWindow> searchWindows(List<ClaimRequest> claimsBySubmission) {
        List<SearchWindow> windows = new ArrayList<>();
        SearchWindow current = null;
        for (ClaimRequest claim : claimsBySubmission) {
            LocalDate submitted = claim.getSubmittedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (current != null && !submitted.plusDays(MARGIN_DAYS).isAfter(current.to())) {
                continue;
            }
            LocalDate from = submitted.minusDays(MARGIN_DAYS);
            current = new SearchWindow(from, from.plusDays(MAX_SEARCH_DAYS - 1L));
            windows.add(current);
        }
        return windows;
    }

    private record SearchWindow(LocalDate from, LocalDate to) {
    }
}
