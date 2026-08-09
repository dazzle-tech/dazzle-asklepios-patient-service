package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "patient_insurance_benefit_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientInsuranceBenefitRule extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_insurance_id", nullable = false)
    private Long patientInsuranceId;

    @Column(name = "eligibility_request_id")
    private Long eligibilityRequestId;

    @Column(name = "benefit_category", nullable = false, length = 255)
    private String benefitCategory;

    @Column(name = "item_name", length = 255)
    private String itemName;

    @Column(name = "item_code", length = 100)
    private String itemCode;

    @Column(name = "network_type", length = 100)
    private String networkType;

    @Column(name = "provider_type", length = 100)
    private String providerType;

    @Column(name = "term", length = 100)
    private String term;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "maximum_benefit", precision = 19, scale = 4)
    private BigDecimal maximumBenefit;

    @Column(name = "approval_limit", precision = 19, scale = 4)
    private BigDecimal approvalLimit;

    @Column(name = "patient_copayment_percentage", precision = 19, scale = 4)
    private BigDecimal patientCopaymentPercentage;

    @Column(name = "patient_maximum_copayment", precision = 19, scale = 4)
    private BigDecimal patientMaximumCopayment;

    @Column(name = "is_global_default", nullable = false)
    @Builder.Default
    private Boolean globalDefault = false;

    @Column(name = "exceptions_json", columnDefinition = "text")
    private String exceptionsJson;
}
