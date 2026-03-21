package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table( name = "encounter_vaccination" )
public class EncounterVaccination extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @NotNull
    @Column(name = "vaccine_id", nullable = false)
    private Long vaccineId;

    @NotNull
    @Column(name = "vaccine_brand_id", nullable = false)
    private Long vaccineBrandId;

    @NotNull
    @Column(name = "vaccine_dose_id", nullable = false)
    private Long vaccineDoseId;

    @Column(name = "vaccine_lot_number", length = 50)
    private String vaccineLotNumber;

    @NotNull
    @Column(name = "date_administered", nullable = false)
    private Instant dateAdministered;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private EncounterVaccinationStatus status = EncounterVaccinationStatus.ACTIVE;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private String reviewedById;

    @Column(name = "administered_location")
    private String administeredLocation;

    @Column(name = "administration_reactions", columnDefinition = "text")
    private String administrationReactions;

    @NotNull
    @Column(name = "is_external_facility", nullable = false)
    @Builder.Default
    private Boolean isExternalFacility = false;

    @Column(name = "external_facility_name")
    private String externalFacilityName;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @AssertTrue(message = "External facility name must be provided when isExternalFacility is true")
    public boolean isExternalFacilityValid() {
        return Boolean.FALSE.equals(isExternalFacility)
                || (externalFacilityName != null && !externalFacilityName.isBlank());
    }
}

