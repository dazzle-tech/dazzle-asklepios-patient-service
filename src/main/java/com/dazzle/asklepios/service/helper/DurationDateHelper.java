package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.domain.enumeration.Unit;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

/**
 * Resolves a "how long ago" duration (unit + whole amount) into a date counted back from today.
 */
@Service
public class DurationDateHelper {

    private static final Set<Unit> SUPPORTED_UNITS = EnumSet.of(Unit.YEARS, Unit.MONTHS, Unit.DAYS);

    // Matches the earliest date the UI date picker allows.
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);

    @Value("${patient.appointment.scheduling.zone}")
    private String defaultZone;

    public record DurationDate(Unit durationType, Integer duration, Date date) {
    }

    public boolean hasDuration(Unit durationType, BigDecimal duration) {
        return durationType != null || duration != null;
    }

    public void requireComplete(
            Unit durationType,
            BigDecimal duration,
            String entityName,
            String errorPrefix
    ) {
        if ((durationType == null) != (duration == null)) {
            throw new BadRequestAlertException(
                    "Duration type and number must be provided together.",
                    entityName,
                    errorPrefix + ".incomplete"
            );
        }
    }

    /**
     * Both duration fields null keeps the manually supplied date. Otherwise the date is calculated,
     * except when the duration equals the saved one: the saved date is kept so it does not drift
     * as days pass.
     */
    public DurationDate resolve(
            Date manualDate,
            Unit durationType,
            BigDecimal duration,
            Unit savedDurationType,
            Integer savedDuration,
            Date savedDate,
            String entityName,
            String errorPrefix
    ) {
        if (!hasDuration(durationType, duration)) {
            return new DurationDate(null, null, manualDate);
        }

        requireComplete(durationType, duration, entityName, errorPrefix);
        int amount = validateAmount(durationType, duration, entityName, errorPrefix);

        boolean unchanged = durationType == savedDurationType
                && Integer.valueOf(amount).equals(savedDuration)
                && savedDate != null;
        if (unchanged) {
            return new DurationDate(durationType, amount, savedDate);
        }

        return new DurationDate(
                durationType,
                amount,
                calculate(durationType, amount, entityName, errorPrefix)
        );
    }

    private int validateAmount(
            Unit durationType,
            BigDecimal duration,
            String entityName,
            String errorPrefix
    ) {
        if (!SUPPORTED_UNITS.contains(durationType)) {
            throw new BadRequestAlertException(
                    "Duration type must be YEARS, MONTHS or DAYS.",
                    entityName,
                    errorPrefix + "Type.invalid"
            );
        }

        if (duration.signum() < 0) {
            throw new BadRequestAlertException(
                    "Duration must not be negative.",
                    entityName,
                    errorPrefix + ".negative"
            );
        }

        if (duration.stripTrailingZeros().scale() > 0) {
            throw new BadRequestAlertException(
                    "Duration must be a whole number.",
                    entityName,
                    errorPrefix + ".fraction"
            );
        }

        try {
            return duration.intValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestAlertException(
                    "Duration is too large.",
                    entityName,
                    errorPrefix + ".overflow"
            );
        }
    }

    private Date calculate(Unit durationType, int amount, String entityName, String errorPrefix) {
        LocalDate today = LocalDate.now(ZoneId.of(defaultZone));
        LocalDate result;

        try {
            // minusMonths/minusYears clamp to the last valid day of the target month.
            result = switch (durationType) {
                case YEARS -> today.minusYears(amount);
                case MONTHS -> today.minusMonths(amount);
                case DAYS -> today.minusDays(amount);
                default -> null;
            };
        } catch (DateTimeException ex) {
            result = null;
        }

        if (result == null || result.isBefore(MIN_DATE)) {
            throw new BadRequestAlertException(
                    "Duration results in an unsupported date.",
                    entityName,
                    errorPrefix + ".outOfRange"
            );
        }

        // Stored at UTC midnight, the same instant Jackson produces for a "yyyy-MM-dd" value.
        return Date.from(result.atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
