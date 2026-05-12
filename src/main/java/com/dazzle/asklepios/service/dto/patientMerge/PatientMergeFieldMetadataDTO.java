package com.dazzle.asklepios.service.dto.patientMerge;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeFieldMetadataDTO {

    private String fieldType;
    private String inputType;
    private String inputSource;
}