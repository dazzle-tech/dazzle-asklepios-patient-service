package com.dazzle.asklepios.service.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * View Model object for storing the patient's key and password.
 */
@Data
public class KeyAndPasswordDTO {

   @NotBlank private String key;

   @NotBlank private String newPassword;
}
