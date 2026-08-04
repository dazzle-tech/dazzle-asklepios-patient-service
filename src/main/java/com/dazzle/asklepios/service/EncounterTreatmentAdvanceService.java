package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Advances registration encounters from {@link TreatmentStatus#PENDING_PAYMENT}
 * once the patient share for billed charge lines is fully covered.
 */
@Service
@Transactional
public class EncounterTreatmentAdvanceService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    EncounterTreatmentAdvanceService.class
            );

    private static final int MONEY_SCALE = 4;

    private static final List<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            List.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private final PatientEncounterRepository patientEncounterRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;

    public EncounterTreatmentAdvanceService(
            PatientEncounterRepository patientEncounterRepository,
            BillingChargeLineRepository billingChargeLineRepository
    ) {
        this.patientEncounterRepository =
                patientEncounterRepository;
        this.billingChargeLineRepository =
                billingChargeLineRepository;
    }

    public void tryAdvanceFromPendingPayment(
            Long encounterId
    ) {
        if (encounterId == null) {
            return;
        }

        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(encounterId)
                        .orElse(null);

        if (encounter == null
                || encounter.getStatus()
                != TreatmentStatus.PENDING_PAYMENT) {
            return;
        }

        List<BillingChargeLine> activeLines =
                billingChargeLineRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_LINE_STATUSES
                        );

        if (activeLines.isEmpty()) {
            return;
        }

        BigDecimal patientRemaining =
                computePatientRemainingToCollect(
                        activeLines
                );

        if (patientRemaining.signum() > 0) {
            LOG.debug(
                    "[ADVANCE_ENCOUNTER] Patient share not fully covered "
                            + "encounterId={} remaining={}",
                    encounterId,
                    patientRemaining
            );
            return;
        }

        TreatmentStatus nextStatus =
                EncounterType.EMERGENCY.equals(
                        encounter.getEncounterType()
                )
                        ? TreatmentStatus.WAITING_TRIAGE
                        : TreatmentStatus.NEW;

        encounter.setStatus(nextStatus);
        patientEncounterRepository.save(encounter);

        LOG.info(
                "[ADVANCE_ENCOUNTER] Moved encounter from PENDING_PAYMENT "
                        + "to {} encounterId={} encounterStatus={}",
                nextStatus,
                encounterId,
                encounter.getEncounterStatus()
        );
    }

    private BigDecimal computePatientRemainingToCollect(
            List<BillingChargeLine> lines
    ) {
        BigDecimal total = BigDecimal.ZERO;

        for (BillingChargeLine line : lines) {
            BigDecimal patientShare =
                    money(
                            line.getPatientResponsibilityAmount()
                    );
            BigDecimal allocated =
                    money(
                            line.getAllocatedAmount()
                    );
            BigDecimal reserved =
                    money(
                            line.getReservedAmount()
                    );

            total =
                    total.add(
                            patientShare
                                    .subtract(allocated)
                                    .subtract(reserved)
                                    .max(BigDecimal.ZERO)
                    );
        }

        return money(total);
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return (value == null
                ? BigDecimal.ZERO
                : value)
                .setScale(
                        MONEY_SCALE,
                        RoundingMode.HALF_UP
                );
    }
}
