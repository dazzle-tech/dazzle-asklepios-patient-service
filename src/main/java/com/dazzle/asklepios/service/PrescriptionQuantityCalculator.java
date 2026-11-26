package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.PrescriptionInstructionClient;
import com.dazzle.asklepios.client.setup.dto.PrescriptionInstructionDTO;
import com.dazzle.asklepios.domain.ApLovValue;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Quantity to dispense for a prescription line: dose x times per day x days, rounded up to a whole number.
 * E.g. 1 tablet once daily for 1 week = 7; 5 ml three times daily for 7 days = 105; 1 tablet weekly for 4 weeks = 4.
 * <p>
 * Works for custom and predefined instructions. Returns null (left to the doctor) when the dose, frequency or
 * duration is missing, for free-text instructions, and for frequencies without a fixed count (with food, as
 * directed...). As needed counts as once a day; Other, and a chronic medication that cannot be calculated (usually
 * no duration), start at 1. Ranges count at their maximum so the patient does not run short.
 * The UI fills the quantity field with the same rules (prescriptionQuantity.ts).
 */
@Service
public class PrescriptionQuantityCalculator {

    private static final Logger LOG = LoggerFactory.getLogger(PrescriptionQuantityCalculator.class);

    /**
     * How often a dose is taken: {@code doses} every {@code days} days (e.g. twice daily = 2 per 1 day,
     * weekly = 1 per 7 days). Ranges count at their maximum ("every 4 to 6 hours" = 6 a day).
     */
    record Rate(int doses, int days) {}

    /**
     * Frequency -> rate. Custom instructions store a MED_FREQUENCY LOV (by its value code), predefined instructions
     * the MedFrequency enum name. Frequencies without a fixed count (as needed, with food, as directed...) are absent.
     */
    static final Map<String, Rate> RATES = Map.ofEntries(
            // MED_FREQUENCY LOV value codes (custom instructions)
            Map.entry("MD_FREQ_QD", new Rate(1, 1)),
            Map.entry("MD_FREQ_QAM", new Rate(1, 1)),
            Map.entry("MD_FREQ_QPM", new Rate(1, 1)),
            Map.entry("MD_FREQ_QHS", new Rate(1, 1)),
            Map.entry("MD_FREQ_BID", new Rate(2, 1)),
            Map.entry("MD_FREQ_Q12H", new Rate(2, 1)),
            Map.entry("MD_FREQ_TID", new Rate(3, 1)),
            Map.entry("MD_FREQ_Q8H", new Rate(3, 1)),
            Map.entry("MD_FREQ_QID", new Rate(4, 1)),
            Map.entry("MD_FREQ_Q6H", new Rate(4, 1)),
            Map.entry("MD_FREQ_Q4H", new Rate(6, 1)),
            Map.entry("MD_FREQ_Q4-6H", new Rate(6, 1)),
            Map.entry("MD_FREQ_QOD", new Rate(1, 2)),
            // As needed counts as once a day (e.g. 2 tablets as needed for 1 day = 2)
            Map.entry("MD_FREQ_PRN", new Rate(1, 1)),
            // MedFrequency enum names (predefined instructions)
            Map.entry("ONCE_DAILY", new Rate(1, 1)),
            Map.entry("EVERY_MORNING", new Rate(1, 1)),
            Map.entry("EVERY_EVENING", new Rate(1, 1)),
            Map.entry("AT_BEDTIME", new Rate(1, 1)),
            Map.entry("EVERY_24_HOURS", new Rate(1, 1)),
            Map.entry("TWICE_DAILY", new Rate(2, 1)),
            Map.entry("EVERY_12_HOURS", new Rate(2, 1)),
            Map.entry("THREE_TIMES_DAILY", new Rate(3, 1)),
            Map.entry("EVERY_8_HOURS", new Rate(3, 1)),
            Map.entry("FOUR_TIMES_DAILY", new Rate(4, 1)),
            Map.entry("EVERY_6_HOURS", new Rate(4, 1)),
            Map.entry("EVERY_4_HOURS", new Rate(6, 1)),
            Map.entry("EVERY_4_TO_6_HOURS", new Rate(6, 1)),
            Map.entry("EVERY_2_TO_3_HOURS", new Rate(12, 1)),
            Map.entry("EVERY_OTHER_DAY", new Rate(1, 2)),
            Map.entry("EVERY_48_HOURS", new Rate(1, 2)),
            Map.entry("EVERY_72_HOURS", new Rate(1, 3)),
            Map.entry("WEEKLY", new Rate(1, 7)),
            Map.entry("EVERY_2_WEEKS", new Rate(1, 14)),
            Map.entry("MONTHLY", new Rate(1, 30)),
            Map.entry("AS_NEEDED", new Rate(1, 1)),
            Map.entry("AS_NEEDED_EVERY_4_TO_6_HOURS", new Rate(1, 1))
    );

    /** Other: no fixed count, so the quantity starts at 1 and the doctor changes it if needed. */
    static final java.util.Set<String> DEFAULT_ONE = java.util.Set.of("MD_FREQ_OTHER", "OTHER");

    /** MED_DURATION value codes -> days. */
    static final Map<String, BigDecimal> DAYS_PER_UNIT = Map.of(
            "MED_DUR_DAYS", BigDecimal.ONE,
            "MED_DUR_WKS", BigDecimal.valueOf(7),
            "MED_DUR_MNTH", BigDecimal.valueOf(30)
    );

    private final ApLovValueRepository apLovValueRepository;
    private final PrescriptionInstructionClient prescriptionInstructionClient;

    public PrescriptionQuantityCalculator(ApLovValueRepository apLovValueRepository, PrescriptionInstructionClient prescriptionInstructionClient) {
        this.apLovValueRepository = apLovValueRepository;
        this.prescriptionInstructionClient = prescriptionInstructionClient;
    }

    /** Quantity for the line, or null when it cannot be calculated. */
    public BigDecimal calculate(PatientPrescriptionMedication medication) {
        if (medication == null) {
            return null;
        }
        BigDecimal quantity = calculateFromInstructions(medication);
        // A chronic medication often has no duration: it starts at 1 and the doctor enters the quantity.
        if (quantity == null && Boolean.TRUE.equals(medication.getChronicMedication())) {
            return BigDecimal.ONE;
        }
        return quantity;
    }

    private BigDecimal calculateFromInstructions(PatientPrescriptionMedication medication) {
        BigDecimal dose;
        String frequency;
        if (PrescriptionInstructionsType.PRE_DEFINED_INSTRUCTIONS.equals(medication.getInstructionsType())) {
            PrescriptionInstructionDTO instruction = predefinedInstruction(medication.getInstructions());
            if (instruction == null) {
                return null;
            }
            dose = instruction.dose();
            frequency = instruction.frequency();
        } else if (PrescriptionInstructionsType.MANUAL_INSTRUCTIONS.equals(medication.getInstructionsType())) {
            return null;
        } else {
            dose = medication.getDose();
            frequency = medication.getFrequency();
        }
        return calculate(dose, frequencyCode(frequency), medication.getDuration(), valueCode(medication.getDurationType()));
    }

    /** dose x doses per period x days / period days, rounded up; null when any part is missing or unknown. */
    static BigDecimal calculate(BigDecimal dose, String frequencyCode, BigDecimal duration, String durationCode) {
        if (frequencyCode != null && DEFAULT_ONE.contains(frequencyCode)) {
            return BigDecimal.ONE;
        }
        Rate rate = frequencyCode == null ? null : RATES.get(frequencyCode);
        BigDecimal daysPerUnit = durationCode == null ? null : DAYS_PER_UNIT.get(durationCode);
        if (dose == null || rate == null || duration == null || daysPerUnit == null
                || dose.signum() <= 0 || duration.signum() <= 0) {
            return null;
        }
        return dose.multiply(BigDecimal.valueOf(rate.doses()))
                .multiply(duration)
                .multiply(daysPerUnit)
                .divide(BigDecimal.valueOf(rate.days()), 0, RoundingMode.CEILING);
    }

    /** LOV key -> its value code; anything else (a MedFrequency enum name) is used as it is. */
    private String frequencyCode(String frequency) {
        if (!StringUtils.hasText(frequency)) {
            return null;
        }
        String code = valueCode(frequency);
        return code != null ? code : frequency.trim();
    }

    private String valueCode(String lovKey) {
        if (!StringUtils.hasText(lovKey)) {
            return null;
        }
        return apLovValueRepository.findById(lovKey.trim())
                .map(ApLovValue::getValueCode)
                .map(String::trim)
                .orElse(null);
    }

    private PrescriptionInstructionDTO predefinedInstruction(String instructionId) {
        if (!StringUtils.hasText(instructionId)) {
            return null;
        }
        try {
            return prescriptionInstructionClient.getPrescriptionInstruction(Long.valueOf(instructionId.trim()));
        } catch (Exception e) {
            LOG.warn("[PRESCRIPTION][QUANTITY] could not load prescription instruction {}: {}", instructionId, e.getMessage());
            return null;
        }
    }
}
