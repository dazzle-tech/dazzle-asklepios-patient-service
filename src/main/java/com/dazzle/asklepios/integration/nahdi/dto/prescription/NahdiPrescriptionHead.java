package com.dazzle.asklepios.integration.nahdi.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * {@code pres_Head} section of the Nahdi SendPrescription request.
 */
@Builder(toBuilder = true)
public record NahdiPrescriptionHead(
        @JsonProperty("provider_id") String providerId,
        @JsonProperty("provider_token") String providerToken,
        @JsonProperty("pres_RefID") String presRefId,
        @JsonProperty("pres_DateTime") String presDateTime,
        @JsonProperty("pres_DateTime2") String presDateTime2,
        @JsonProperty("pres_Status") String presStatus,
        @JsonProperty("pres_TYPE") String presType,
        @JsonProperty("pres_DocCode") String presDocCode,
        @JsonProperty("pres_DocName") String presDocName,
        @JsonProperty("pres_DocSpe") String presDocSpe,
        @JsonProperty("pres_Note") String presNote,
        @JsonProperty("patient_FileNo") String patientFileNo,
        @JsonProperty("patient_NameEn") String patientNameEn,
        @JsonProperty("patient_NameAr") String patientNameAr,
        @JsonProperty("patient_Gender") String patientGender,
        @JsonProperty("patient_DOB") String patientDob,
        @JsonProperty("patient_Age") String patientAge,
        @JsonProperty("patient_Allergy") String patientAllergy,
        @JsonProperty("patient_MobileNo") String patientMobileNo,
        @JsonProperty("patient_NationalID") String patientNationalId,
        @JsonProperty("patient_InsExpDate") String patientInsExpDate,
        @JsonProperty("patient_InsCardNo") String patientInsCardNo,
        @JsonProperty("patient_InsPolicyNo") String patientInsPolicyNo,
        @JsonProperty("patient_InsClass") String patientInsClass,
        @JsonProperty("patient_InsPolicyName") String patientInsPolicyName,
        @JsonProperty("patient_InsCOMPANY") String patientInsCompany,
        @JsonProperty("patient_InsVisitPaid") BigDecimal patientInsVisitPaid,
        @JsonProperty("patient_InsMaxCoPay") BigDecimal patientInsMaxCoPay,
        @JsonProperty("patient_InsCoPayPercent") String patientInsCoPayPercent,
        @JsonProperty("patient_InsCoPayDiscountPercent") String patientInsCoPayDiscountPercent,
        @JsonProperty("C_BRANDCOPAYPER") String brandCoPayPercent,
        @JsonProperty("C_BRANDCOPAYMAX") String brandCoPayMax,
        @JsonProperty("C_GENCOPAYPER") String genericCoPayPercent,
        @JsonProperty("C_GENCOPAYMAX") String genericCoPayMax,
        @JsonProperty("C_INS_DISCOUNT") String insuranceDiscount,
        @JsonProperty("weight") String weight,
        @JsonProperty("hight") String height,
        @JsonProperty("temperature") String temperature,
        @JsonProperty("bp_high") String bpHigh,
        @JsonProperty("bp_low") String bpLow,
        @JsonProperty("pulse_rate") String pulseRate,
        @JsonProperty("pregnant") String pregnant,
        @JsonProperty("lactating") String lactating
) {

    @Override
    public String toString() {
        return "NahdiPrescriptionHead{presRefId='" + presRefId + "', patientFileNo='" + patientFileNo + "'}";
    }
}
