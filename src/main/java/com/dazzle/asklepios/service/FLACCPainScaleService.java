package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ApLovValue;
import com.dazzle.asklepios.domain.FLACCPainScale;
import com.dazzle.asklepios.domain.enumeration.FLACCPainLevel;
import com.dazzle.asklepios.domain.enumeration.FLACCPainScaleStatus;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import com.dazzle.asklepios.repository.FLACCPainScaleRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.flaccPainScale.FLACCPainScaleCreateDTO;
import com.dazzle.asklepios.service.dto.flaccPainScale.FLACCPainScaleUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class FLACCPainScaleService {

    private static final Logger LOG =
            LoggerFactory.getLogger(FLACCPainScaleService.class);

    private final FLACCPainScaleRepository flaccPainScaleRepository;
    private final ApLovValueRepository apLovValueRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public FLACCPainScale create(FLACCPainScaleCreateDTO dto) {
        LOG.info("[CREATE] FLACCPainScale payload={}", dto);

        validatePatientAndEncounterIds(
                dto.getPatientId(),
                dto.getEncounterId()
        );

        validateLovKeys(
                dto.getFace(),
                dto.getLegs(),
                dto.getActivity(),
                dto.getCry(),
                dto.getConsolability()
        );

        int totalScore = calculateTotalScore(
                dto.getFace(),
                dto.getLegs(),
                dto.getActivity(),
                dto.getCry(),
                dto.getConsolability()
        );

        validateTotalScore(totalScore);


        FLACCPainScale flaccPainScale = FLACCPainScale.builder()
                .patientId(dto.getPatientId())
                .encounterId(dto.getEncounterId())
                .face(dto.getFace())
                .legs(dto.getLegs())
                .activity(dto.getActivity())
                .cry(dto.getCry())
                .consolability(dto.getConsolability())
                .totalScore(totalScore)
                .painLevel(calculatePainLevel(totalScore))
                .status(FLACCPainScaleStatus.ACTIVE)
                .build();

        FLACCPainScale saved =
                flaccPainScaleRepository.save(flaccPainScale);

        LOG.info(
                "[CREATE] Successfully created FLACCPainScale id={} patientId={} encounterId={} totalScore={}",
                saved.getId(),
                saved.getPatientId(),
                saved.getEncounterId(),
                saved.getTotalScore()
        );

        return saved;
    }

    public FLACCPainScale update(FLACCPainScaleUpdateDTO dto) {
        LOG.info(
                "[UPDATE] FLACCPainScale id={} payload={}",
                dto.getId(),
                dto
        );

        if (dto.getId() == null || dto.getId() <= 0) {
            throw new BadRequestAlertException(
                    "FLACC Pain Scale id is required.",
                    "flaccPainScale",
                    "flaccPainScale.id.invalid"
            );
        }

        validatePatientAndEncounterIds(
                dto.getPatientId(),
                dto.getEncounterId()
        );

        validateLovKeys(
                dto.getFace(),
                dto.getLegs(),
                dto.getActivity(),
                dto.getCry(),
                dto.getConsolability()
        );

        FLACCPainScale existing =
                flaccPainScaleRepository.findById(dto.getId())
                        .orElseThrow(() -> {
                            LOG.warn(
                                    "[UPDATE] FLACCPainScale not found id={}",
                                    dto.getId()
                            );

                            return new BadRequestAlertException(
                                    "FLACC Pain Scale not found.",
                                    "flaccPainScale",
                                    "flaccPainScale.notFound"
                            );
                        });

        if (existing.getStatus() == FLACCPainScaleStatus.CANCELLED) {
            LOG.warn(
                    "[UPDATE] Cannot update cancelled FLACCPainScale id={}",
                    dto.getId()
            );

            throw new BadRequestAlertException(
                    "Cancelled FLACC Pain Scale cannot be updated.",
                    "flaccPainScale",
                    "flaccPainScale.cancelled"
            );
        }

        int totalScore = calculateTotalScore(
                dto.getFace(),
                dto.getLegs(),
                dto.getActivity(),
                dto.getCry(),
                dto.getConsolability()
        );

        validateTotalScore(totalScore);

        existing.setPatientId(dto.getPatientId());
        existing.setEncounterId(dto.getEncounterId());
        existing.setFace(dto.getFace());
        existing.setLegs(dto.getLegs());
        existing.setActivity(dto.getActivity());
        existing.setCry(dto.getCry());
        existing.setConsolability(dto.getConsolability());
        existing.setTotalScore(totalScore);
        existing.setPainLevel(
                calculatePainLevel(totalScore)
        );

        FLACCPainScale updated =
                flaccPainScaleRepository.save(existing);

        LOG.info(
                "[UPDATE] Successfully updated FLACCPainScale id={} totalScore={}",
                updated.getId(),
                updated.getTotalScore()
        );

        return updated;
    }

    private void validatePatientAndEncounterIds(
            Long patientId,
            Long encounterId
    ) {
        if (patientId == null || patientId <= 0) {
            throw new BadRequestAlertException(
                    "Patient id is required.",
                    "flaccPainScale",
                    "patient.id.invalid"
            );
        }

        if (!patientRepository.existsById(patientId)) {
            LOG.warn(
                    "[VALIDATION] Patient not found id={}",
                    patientId
            );

            throw new BadRequestAlertException(
                    "Patient not found.",
                    "flaccPainScale",
                    "patient.notFound"
            );
        }

        if (encounterId == null || encounterId <= 0) {
            throw new BadRequestAlertException(
                    "Encounter id is required.",
                    "flaccPainScale",
                    "encounter.id.invalid"
            );
        }

        if (!patientEncounterRepository.existsById(encounterId)) {
            LOG.warn(
                    "[VALIDATION] Encounter not found id={}",
                    encounterId
            );

            throw new BadRequestAlertException(
                    "Encounter not found.",
                    "flaccPainScale",
                    "encounter.notFound"
            );
        }
    }

    private void validateLovKeys(
            String faceLov,
            String legsLov,
            String activityLov,
            String cryLov,
            String consolabilityLov
    ) {
        validateLovKey(faceLov, "Face");
        validateLovKey(legsLov, "Legs");
        validateLovKey(activityLov, "Activity");
        validateLovKey(cryLov, "Cry");
        validateLovKey(consolabilityLov, "Consolability");
    }

    private void validateLovKey(
            String lovKey,
            String fieldName
    ) {
        if (lovKey == null || lovKey.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    fieldName + " LOV value is required.",
                    "flaccPainScale",
                    "flaccPainScale." + fieldName.toLowerCase() + ".required"
            );
        }

        List<ApLovValue> values =
                apLovValueRepository.findByKeyIn(List.of(lovKey));

        if (values.isEmpty()) {
            LOG.warn(
                    "[VALIDATION] LOV value not found field={} key={}",
                    fieldName,
                    lovKey
            );

            throw new BadRequestAlertException(
                    fieldName + " LOV value not found.",
                    "flaccPainScale",
                    "flaccPainScale.lov.notFound"
            );
        }

        ApLovValue value = values.get(0);

        if (value.getScore() == null) {
            LOG.warn(
                    "[VALIDATION] LOV value has no score field={} key={}",
                    fieldName,
                    lovKey
            );

            throw new BadRequestAlertException(
                    fieldName + " LOV value has no score.",
                    "flaccPainScale",
                    "flaccPainScale.lov.scoreMissing"
            );
        }
    }

    private int calculateTotalScore(
            String faceLov,
            String legsLov,
            String activityLov,
            String cryLov,
            String consolabilityLov
    ) {
        List<String> keys = List.of(
                faceLov,
                legsLov,
                activityLov,
                cryLov,
                consolabilityLov
        );

        LOG.debug(
                "[SCORE] Calculating FLACC total score for lovKeys={}",
                keys
        );

        List<ApLovValue> lovValues =
                apLovValueRepository.findByKeyIn(keys);

        int totalScore = lovValues.stream()
                .map(ApLovValue::getScore)
                .filter(Objects::nonNull)
                .mapToInt(BigDecimal::intValue)
                .sum();

        LOG.debug(
                "[SCORE] Calculated FLACC total score={} for lovKeys={}",
                totalScore,
                keys
        );

        return totalScore;
    }

    private void validateTotalScore(int totalScore) {
        if (totalScore < 0 || totalScore > 10) {
            LOG.warn(
                    "[VALIDATION] Invalid FLACC total score={}",
                    totalScore
            );

            throw new BadRequestAlertException(
                    "FLACC total score must be between 0 and 10.",
                    "flaccPainScale",
                    "score.invalid"
            );
        }
    }

    @Transactional(readOnly = true)
    public FLACCPainScale findById(Long id) {
        LOG.debug("[GET] FLACCPainScale id={}", id);

        if (id == null || id <= 0) {
            throw new BadRequestAlertException(
                    "FLACC Pain Scale id is required.",
                    "flaccPainScale",
                    "flaccPainScale.id.invalid"
            );
        }

        FLACCPainScale flaccPainScale =
                flaccPainScaleRepository.findById(id)
                        .orElseThrow(() -> {
                            LOG.warn(
                                    "[GET] FLACCPainScale not found id={}",
                                    id
                            );

                            return new BadRequestAlertException(
                                    "FLACC Pain Scale not found.",
                                    "flaccPainScale",
                                    "flaccPainScale.notFound"
                            );
                        });

        LOG.debug(
                "[GET] FLACCPainScale found id={} totalScore={} painLevel={} status={}",
                flaccPainScale.getId(),
                flaccPainScale.getTotalScore(),
                flaccPainScale.getPainLevel(),
                flaccPainScale.getStatus()
        );

        return flaccPainScale;
    }

    @Transactional(readOnly = true)
    public List<FLACCPainScale> findByPatientId(Long patientId) {
        LOG.debug(
                "[LIST] FLACCPainScales by patientId={}",
                patientId
        );

        if (patientId == null || patientId <= 0) {
            throw new BadRequestAlertException(
                    "Patient id is required.",
                    "flaccPainScale",
                    "patient.id.invalid"
            );
        }

        if (!patientRepository.existsById(patientId)) {
            LOG.warn(
                    "[LIST] Patient not found id={}",
                    patientId
            );

            throw new BadRequestAlertException(
                    "Patient not found.",
                    "flaccPainScale",
                    "patient.notFound"
            );
        }

        List<FLACCPainScale> records =
                flaccPainScaleRepository.findByPatientIdOrderByCreatedDateDesc(patientId);

        LOG.debug(
                "[LIST] FLACCPainScales by patientId={} result size={}",
                patientId,
                records.size()
        );

        return records;
    }

    @Transactional(readOnly = true)
    public List<FLACCPainScale> findByEncounterId(
            Long encounterId,
            boolean showCancelled
    ) {
        LOG.debug(
                "[LIST] FLACCPainScales by encounterId={} showCancelled={}",
                encounterId,
                showCancelled
        );

        if (encounterId == null || encounterId <= 0) {
            throw new BadRequestAlertException(
                    "Encounter id is required.",
                    "flaccPainScale",
                    "encounter.id.invalid"
            );
        }

        if (!patientEncounterRepository.existsById(encounterId)) {
            LOG.warn(
                    "[LIST] Encounter not found id={}",
                    encounterId
            );

            throw new BadRequestAlertException(
                    "Encounter not found.",
                    "flaccPainScale",
                    "encounter.notFound"
            );
        }

        List<FLACCPainScale> records;

        if (showCancelled) {
            records =
                    flaccPainScaleRepository.findByEncounterIdOrderByCreatedDateDesc(encounterId);
        } else {
            records =
                    flaccPainScaleRepository
                            .findByEncounterIdAndStatusNotOrderByCreatedDateDesc(
                                    encounterId,
                                    FLACCPainScaleStatus.CANCELLED
                            );
        }

        LOG.debug(
                "[LIST] FLACCPainScales by encounterId={} showCancelled={} result size={}",
                encounterId,
                showCancelled,
                records.size()
        );

        return records;
    }

    public FLACCPainScale cancel(
            Long id,
            String cancellationReason
    ) {
        String currentUsername =
                SecurityUtils.getCurrentUserLogin().orElse("unknown");

        LOG.info(
                "[CANCEL] FLACCPainScale id={} cancelledBy={} reason={}",
                id,
                currentUsername,
                cancellationReason
        );

        if (id == null || id <= 0) {
            throw new BadRequestAlertException(
                    "FLACC Pain Scale id is required.",
                    "flaccPainScale",
                    "flaccPainScale.id.invalid"
            );
        }

        validateCancellationReason(cancellationReason);

        FLACCPainScale existing =
                flaccPainScaleRepository.findById(id)
                        .orElseThrow(() -> {
                            LOG.warn(
                                    "[CANCEL] FLACCPainScale not found id={}",
                                    id
                            );

                            return new BadRequestAlertException(
                                    "FLACC Pain Scale not found.",
                                    "flaccPainScale",
                                    "flaccPainScale.notFound"
                            );
                        });

        if (existing.getStatus() == FLACCPainScaleStatus.CANCELLED) {
            LOG.warn(
                    "[CANCEL] FLACCPainScale already cancelled id={}",
                    id
            );

            throw new BadRequestAlertException(
                    "FLACC Pain Scale is already cancelled.",
                    "flaccPainScale",
                    "flaccPainScale.alreadyCancelled"
            );
        }

        existing.setStatus(FLACCPainScaleStatus.CANCELLED);
        existing.setCancellationReason(
                cancellationReason.trim()
        );
        existing.setCancelledAt(Instant.now());
        existing.setCancelledBy(currentUsername);

        FLACCPainScale cancelled =
                flaccPainScaleRepository.save(existing);

        LOG.info(
                "[CANCEL] Successfully cancelled FLACCPainScale id={} cancelledBy={}",
                cancelled.getId(),
                currentUsername
        );

        return cancelled;
    }

    private void validateCancellationReason(
            String cancellationReason
    ) {
        if (cancellationReason == null
                || cancellationReason.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required.",
                    "flaccPainScale",
                    "cancellationReason.required"
            );
        }

        if (cancellationReason.trim().length() > 255) {
            throw new BadRequestAlertException(
                    "Cancellation reason must not exceed 255 characters.",
                    "flaccPainScale",
                    "cancellationReason.maxLength"
            );
        }
    }

    // IMPORTANT: If you change the scoring here, update the frontend scoring accordingly so the displayed level matches the stored value.
    private FLACCPainLevel calculatePainLevel(
            int totalScore
    ) {
        if (totalScore == 0) {
            return FLACCPainLevel.NO_PAIN;
        }

        if (totalScore <= 3) {
            return FLACCPainLevel.MILD_PAIN;
        }

        if (totalScore <= 7) {
            return FLACCPainLevel.MODERATE_PAIN;
        }

        return FLACCPainLevel.SEVERE_PAIN;
    }

    @Transactional(readOnly = true)
    public FLACCPainScale findLatestActiveByEncounterId(Long encounterId) {
        LOG.debug(
                "[GET] Latest active FLACCPainScale by encounterId={}",
                encounterId
        );

        if (encounterId == null || encounterId <= 0) {
            throw new BadRequestAlertException(
                    "Encounter id is required.",
                    "flaccPainScale",
                    "encounter.id.invalid"
            );
        }

        if (!patientEncounterRepository.existsById(encounterId)) {
            LOG.warn(
                    "[GET] Encounter not found id={}",
                    encounterId
            );

            throw new BadRequestAlertException(
                    "Encounter not found.",
                    "flaccPainScale",
                    "encounter.notFound"
            );
        }

        FLACCPainScale result =
                flaccPainScaleRepository
                        .findFirstByEncounterIdAndStatusOrderByCreatedDateDesc(
                                encounterId,
                                FLACCPainScaleStatus.ACTIVE
                        )
                        .orElse(null);

        if (result != null) {

            LOG.debug(
                    "[GET] Latest active FLACCPainScale found id={} encounterId={} totalScore={}",
                    result.getId(),
                    result.getEncounterId(),
                    result.getTotalScore()
            );
        } else {
            LOG.debug(
                    "[GET] No active FLACCPainScale found for encounterId={}",
                    encounterId
            );
        }

        return result;
    }
}