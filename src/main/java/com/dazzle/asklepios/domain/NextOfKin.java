package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.RelationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
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
@Table(name = "next_of_kin")
public class NextOfKin extends AbstractAuditingEntity<Long> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false, foreignKey = @ForeignKey(name = "fk_next_of_kin_patient"))
    private Patient patient;

    @NotEmpty
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotNull
    @Column(name = "relationship", nullable = false, length = 100)
    private RelationType relationship;

    @NotEmpty
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @NotEmpty
    @Email
    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @NotEmpty
    @Column(name = "mobile_number", nullable = false, length = 50)
    private String mobileNumber;


    @Column(name = "telephone", length = 50)
    private String telephone;

    @Column(name = "international_number", length = 50)
    private String internationalNumber;

    @Column(name = "landline_number", length = 50)
    private String landlineNumber;
}
