package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Table(name = "address")
public class Address extends AbstractAuditingEntity<Long> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "country", length = 255)
    private String country;

    @Column(name = "state_province", length = 255)
    private String stateProvince;

    @Column(name = "city", length = 255)
    private String city;

    @Column(name = "street_name", length = 255)
    private String streetName;

    @Column(name = "house_apartment_number", length = 50)
    private String houseApartmentNumber;

    @Column(name = "postal_zip_code", length = 20)
    private String postalZipCode;

    @Column(name = "additional_address_line", length = 255)
    private String additionalAddressLine;

    @Column(name = "country_id", length = 50, unique = true)
    private String countryId;

    @NotNull
    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent;
}
