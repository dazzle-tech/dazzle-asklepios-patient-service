package com.dazzle.asklepios.service.validation;

import com.dazzle.asklepios.domain.enumeration.MedicationInstructionType;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderUpdateDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MedicationInstructionValidator implements ConstraintValidator<ValidMedicationInstruction, Object> {

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
        String frequency = null;

        if (value instanceof UrgentCareMedicationOrderCreateDTO dto) {
            instructionType = dto.instructionType();
            instructionText = dto.instructionText();
            dose = dto.dose();
            doseUnit = dto.doseUnit();
            route = dto.route();
            frequency = dto.frequency();
        } else if (value instanceof UrgentCareMedicationOrderUpdateDTO dto) {
            instructionType = dto.instructionType();
            instructionText = dto.instructionText();
            dose = dto.dose();
            doseUnit = dto.doseUnit();
            route = dto.route();
            frequency = dto.frequency();
        }

        if (instructionType == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (instructionType == MedicationInstructionType.MANUAL_INSTRUCTIONS) {
            boolean valid = instructionText != null && !instructionText.isBlank();

            if (!valid) {
                context.buildConstraintViolationWithTemplate(
                                "instructionText is mandatory when instructionType is MANUAL_INSTRUCTIONS")
                        .addPropertyNode("instructionText")
                        .addConstraintViolation();
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

            if (frequency == null || frequency.isBlank()) {
                context.buildConstraintViolationWithTemplate(
                                "frequency is mandatory when instructionType is CUSTOM_INSTRUCTIONS")
                        .addPropertyNode("frequency")
                        .addConstraintViolation();
                valid = false;
            }

            return valid;
        }

        return true;
    }
}