package com.dazzle.asklepios.validation;

import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentCreateDTO;
import com.dazzle.asklepios.service.dto.patientPayments.PatientPaymentServiceItemDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;
import java.util.List;

public class PatientPaymentCreateValidator
        implements ConstraintValidator<ValidPatientPaymentCreate, PatientPaymentCreateDTO> {

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

    @Override
    public boolean isValid(PatientPaymentCreateDTO dto, ConstraintValidatorContext context) {

        if (dto == null) return true;

        List<PatientPaymentServiceItemDTO> services =
                dto.services() == null ? List.of() : dto.services();

        BigDecimal dueAmount = services.stream()
                .filter(s -> !Boolean.TRUE.equals(s.isExempted()))
                .map(PatientPaymentServiceItemDTO::price)
                .reduce(ZERO_AMOUNT, BigDecimal::add);

        boolean hasBillable = dueAmount.compareTo(ZERO_AMOUNT) > 0;

        if (!hasBillable) return true;

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (dto.paymentTypes() == null) {
            context.buildConstraintViolationWithTemplate("paymentTypes required")
                    .addPropertyNode("paymentTypes").addConstraintViolation();
            valid = false;
        }

        if (dto.paymentMethods() == null) {
            context.buildConstraintViolationWithTemplate("paymentMethods required")
                    .addPropertyNode("paymentMethods").addConstraintViolation();
            valid = false;
        }

        if (dto.amount() == null) {
            context.buildConstraintViolationWithTemplate("amount required")
                    .addPropertyNode("amount").addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}