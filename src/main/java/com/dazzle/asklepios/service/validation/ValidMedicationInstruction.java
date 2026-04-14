package com.dazzle.asklepios.service.validation;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MedicationInstructionValidator.class)
@Documented
public @interface ValidMedicationInstruction {
    String message() default "Invalid medication instruction fields";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
