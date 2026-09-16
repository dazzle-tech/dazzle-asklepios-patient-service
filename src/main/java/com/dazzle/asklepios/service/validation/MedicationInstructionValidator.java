package com.dazzle.asklepios.service.validation;

import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.domain.enumeration.Unit;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderUpdateDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MedicationInstructionValidator
        implements ConstraintValidator<ValidMedicationInstruction, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        MedicationInstructionType instructionType = null;
        String instructionText = null;
        Long dose = null;
        String doseUnit = null;
        String route = null;

        Integer frequencyNumber = null;
        Unit frequencyUnit = null;
        Integer duration = null;
        java.time.LocalTime startTime = null;

        if (value instanceof UrgentCareMedicationOrderCreateDTO dto) {

            instructionType = dto.instructionType();
            instructionText = dto.instructionText();
            dose = dto.dose();
            doseUnit = dto.doseUnit();
            route = dto.route();

            frequencyNumber = dto.frequencyNumber();
            frequencyUnit = dto.frequencyUnit();
            duration = dto.duration();
            startTime = dto.startTime();

        } else if (value instanceof UrgentCareMedicationOrderUpdateDTO dto) {

            instructionType = dto.instructionType();
            instructionText = dto.instructionText();
            dose = dto.dose();
            doseUnit = dto.doseUnit();
            route = dto.route();

            frequencyNumber = dto.frequencyNumber();
            frequencyUnit = dto.frequencyUnit();
            duration = dto.duration();
            startTime = dto.startTime();
        }

        if (instructionType == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (instructionType == MedicationInstructionType.MANUAL_INSTRUCTIONS) {

            boolean valid = true;

            if (instructionText == null || instructionText.isBlank()) {
                context.buildConstraintViolationWithTemplate(
                                "instructionText is mandatory when instructionType is MANUAL_INSTRUCTIONS")
                        .addPropertyNode("instructionText")
                        .addConstraintViolation();

                valid = false;
            }

            return valid;
        }

        if (instructionType == MedicationInstructionType.CUSTOM_INSTRUCTIONS) {

            boolean valid = true;

            if (dose == null) {
                context.buildConstraintViolationWithTemplate(
                                "dose is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("dose")
                        .addConstraintViolation();

                valid = false;
            } else if (dose <= 0) {
                context.buildConstraintViolationWithTemplate(
                                "dose must be greater than 0")
                        .addPropertyNode("dose")
                        .addConstraintViolation();

                valid = false;
            }

            if (doseUnit == null || doseUnit.isBlank()) {
                context.buildConstraintViolationWithTemplate(
                                "doseUnit is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("doseUnit")
                        .addConstraintViolation();

                valid = false;
            }

            if (route == null || route.isBlank()) {
                context.buildConstraintViolationWithTemplate(
                                "route is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("route")
                        .addConstraintViolation();

                valid = false;
            }

            // Frequency Number
            if (frequencyNumber == null) {

                context.buildConstraintViolationWithTemplate(
                                "frequencyNumber is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("frequencyNumber")
                        .addConstraintViolation();

                valid = false;

            } else if (frequencyNumber <= 0) {

                context.buildConstraintViolationWithTemplate(
                                "frequencyNumber must be greater than 0")
                        .addPropertyNode("frequencyNumber")
                        .addConstraintViolation();

                valid = false;
            }

            // Frequency Unit
            if (frequencyUnit == null) {

                context.buildConstraintViolationWithTemplate(
                                "frequencyUnit is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("frequencyUnit")
                        .addConstraintViolation();

                valid = false;

            } else if (frequencyUnit != Unit.MINUTES && frequencyUnit != Unit.HOURS) {

                context.buildConstraintViolationWithTemplate(
                                "frequencyUnit must be MINUTES or HOURS")
                        .addPropertyNode("frequencyUnit")
                        .addConstraintViolation();

                valid = false;
            }

            // Duration
            if (duration == null) {

                context.buildConstraintViolationWithTemplate(
                                "duration is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("duration")
                        .addConstraintViolation();

                valid = false;

            } else if (duration <= 0) {

                context.buildConstraintViolationWithTemplate(
                                "duration must be greater than 0")
                        .addPropertyNode("duration")
                        .addConstraintViolation();

                valid = false;
            }

            // Start Time
            if (startTime == null) {

                context.buildConstraintViolationWithTemplate(
                                "startTime is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("startTime")
                        .addConstraintViolation();

                valid = false;
            }

            return valid;
        }

        return true;
    }
}
