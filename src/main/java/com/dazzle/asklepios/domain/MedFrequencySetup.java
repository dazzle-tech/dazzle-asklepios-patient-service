package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.FrequencyList;
import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of the setup-owned {@code med_frequency_setup} table:
 * how a MedFrequency is split into doses, e.g. EVERY_2_HOURS -> 2 HOURS.
 */
@Entity
@Immutable
@Table(name = "med_frequency_setup")
@Getter
@NoArgsConstructor
public class MedFrequencySetup {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency")
    private MedFrequency frequency;

    @Column(name = "frequency_number")
    private Integer frequencyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_unit")
    private FrequencyList frequencyUnit;

    @Column(name = "is_active")
    private Boolean isActive;
}
