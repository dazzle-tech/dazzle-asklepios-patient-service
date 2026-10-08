package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.ClaimEncounterDiagnosis;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.ClaimEncounterDiagnosisRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterDiagnosisRequest;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterDiagnosisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClaimEncounterDiagnosisService {

    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;
    private final ClaimEncounterDiagnosisRepository claimEncounterDiagnosisRepository;

    @Transactional(readOnly = true)
    public List<ClaimEncounterDiagnosisResponse> findByEncounterId(
            Long encounterId
    ) {
        ClaimEncounterCopy copy = claimEncounterCopyRepository
                .findByEncounterId(encounterId)
                .orElse(null);

        if (copy == null) {
            return List.of();
        }

        return claimEncounterDiagnosisRepository
                .findByClaimEncounterCopyId(copy.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public List<ClaimEncounterDiagnosisResponse> update(
            Long encounterId,
            List<ClaimEncounterDiagnosisRequest> requests
    ) {
        ClaimEncounterCopy copy = claimEncounterCopyRepository
                .findByEncounterId(encounterId)
                .orElse(null);

        if (copy == null) {
            return List.of();
        }

        List<ClaimEncounterDiagnosis> existing =
                claimEncounterDiagnosisRepository
                        .findByClaimEncounterCopyId(copy.getId());

        Map<Long, ClaimEncounterDiagnosis> existingById =
                existing.stream()
                        .filter(diagnosis -> diagnosis.getId() != null)
                        .collect(Collectors.toMap(
                                ClaimEncounterDiagnosis::getId,
                                Function.identity()
                        ));

        List<ClaimEncounterDiagnosis> updated = new ArrayList<>();

        for (ClaimEncounterDiagnosisRequest request : requests) {

            ClaimEncounterDiagnosis diagnosis;

            if (request.id() != null) {
                diagnosis = existingById.get(request.id());

                if (diagnosis == null) {
                    continue;
                }
            } else {
                diagnosis = new ClaimEncounterDiagnosis();
                diagnosis.setClaimEncounterCopyId(copy.getId());
                diagnosis.setEncounterId(encounterId);
            }

            diagnosis.setDiagnosisId(request.diagnosisId());
            diagnosis.setType(request.type());
            diagnosis.setSuspected(
                    Boolean.TRUE.equals(request.suspected())
            );
            diagnosis.setMajor(
                    Boolean.TRUE.equals(request.major())
            );

            updated.add(diagnosis);
        }

        List<Long> requestedIds = requests.stream()
                .map(ClaimEncounterDiagnosisRequest::id)
                .filter(id -> id != null)
                .toList();

        List<ClaimEncounterDiagnosis> deleted = existing.stream()
                .filter(diagnosis ->
                        diagnosis.getId() != null &&
                                !requestedIds.contains(diagnosis.getId())
                )
                .toList();

        if (!deleted.isEmpty()) {
            claimEncounterDiagnosisRepository.deleteAll(deleted);
        }

        return claimEncounterDiagnosisRepository
                .saveAll(updated)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ClaimEncounterDiagnosisResponse mapToResponse(
            ClaimEncounterDiagnosis diagnosis
    ) {
        return new ClaimEncounterDiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getClaimEncounterCopyId(),
                diagnosis.getEncounterId(),
                diagnosis.getDiagnosisId(),
                diagnosis.getType(),
                diagnosis.getSuspected(),
                diagnosis.getMajor()
        );
    }
}