package com.dazzle.asklepios.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "surgical_history")
@EqualsAndHashCode(callSuper = false)
public class SurgicalHistory extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @NotNull
    @NotBlank
    @Column(name = "surgery", nullable = false)
    private String surgery;

    @NotNull
    @Column(name = "date_of_surgery", nullable = false)
    private Date dateOfSurgery;

    @NotNull
    @NotBlank
    @Column(name = "facility", nullable = false)
    private String facility;

    @NotNull
    @Column(name = "anesthesia_type", length = 100)
    private String anesthesiaType;

    @Column(name = "complications", length = 255)
    private String complications;

    @Column(name = "adverse_reactions_to_anesthesia", length = 1000)
    private String adverseReactionsToAnesthesia;

    @Column(name = "has_implants_or_devices")
    private Boolean hasImplantsOrDevices;

    @Column(name = "implants_or_devices_description", length = 1000)
    private String implantsOrDevicesDescription;
}
