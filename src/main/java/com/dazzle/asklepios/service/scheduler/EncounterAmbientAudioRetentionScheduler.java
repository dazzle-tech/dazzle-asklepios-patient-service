package com.dazzle.asklepios.service.scheduler;

import com.dazzle.asklepios.client.setup.AmbientScribeClient;
import com.dazzle.asklepios.client.setup.dto.AmbientScribeAudioRetentionDTO;
import com.dazzle.asklepios.repository.EncounterAmbientRepository;
import com.dazzle.asklepios.service.EncounterAmbientService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EncounterAmbientAudioRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAmbientAudioRetentionScheduler.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_BATCHES = 50;

    private final AmbientScribeClient ambientScribeClient;
    private final EncounterAmbientRepository encounterAmbientRepository;
    private final EncounterAmbientService encounterAmbientService;

    public EncounterAmbientAudioRetentionScheduler(
        AmbientScribeClient ambientScribeClient,
        EncounterAmbientRepository encounterAmbientRepository,
        EncounterAmbientService encounterAmbientService
    ) {
        this.ambientScribeClient = ambientScribeClient;
        this.encounterAmbientRepository = encounterAmbientRepository;
        this.encounterAmbientService = encounterAmbientService;
    }

    @Scheduled(
        cron = "${patient.ambient-scribe.audio-retention.cron:0 30 2 * * *}",
        zone = "${patient.ambient-scribe.audio-retention.zone:Asia/Gaza}"
    )
    public void clearExpiredRecordings() {
        List<AmbientScribeAudioRetentionDTO> settings;
        try {
            settings = ambientScribeClient.listAudioRetention();
        } catch (RuntimeException exception) {
            LOG.warn("[AUDIO_RETENTION] could not read ambient scribe setup: {}", exception.getMessage());
            return;
        }
        if (settings == null || settings.isEmpty()) {
            LOG.debug("[AUDIO_RETENTION] no ambient scribe audio retention settings");
            return;
        }

        Instant now = Instant.now();
        int cleared = 0;
        for (AmbientScribeAudioRetentionDTO setting : settings) {
            Integer days = retentionDays(setting.audioRetention());
            if (setting.facilityId() == null || days == null) {
                continue;
            }
            cleared += clearFacility(setting.facilityId(), now.minus(days, ChronoUnit.DAYS));
        }
        LOG.info("[AUDIO_RETENTION] cleared {} recordings", cleared);
    }

    private int clearFacility(Long facilityId, Instant cutoff) {
        int cleared = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> ids = encounterAmbientRepository.findRecordingIdsDueForClearance(
                facilityId,
                cutoff,
                PageRequest.of(0, BATCH_SIZE)
            );
            if (ids.isEmpty()) {
                break;
            }
            int clearedInBatch = 0;
            for (Long id : ids) {
                try {
                    if (encounterAmbientService.clearExpiredRecording(id)) {
                        clearedInBatch++;
                    }
                } catch (RuntimeException exception) {
                    LOG.warn("[AUDIO_RETENTION] failed to clear recording id={}: {}", id, exception.getMessage());
                }
            }
            cleared += clearedInBatch;
            if (clearedInBatch == 0) {
                break;
            }
        }
        return cleared;
    }

    static Integer retentionDays(String audioRetention) {
        if (audioRetention == null) {
            return null;
        }
        return switch (audioRetention) {
            case "DAYS_7" -> 7;
            case "DAYS_30" -> 30;
            case "DAYS_90" -> 90;
            case "DAYS_365" -> 365;
            default -> null;
        };
    }
}
