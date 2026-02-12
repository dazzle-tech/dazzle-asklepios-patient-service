package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Relations;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "family_history")
@EqualsAndHashCode(callSuper = false)
public class FamilyHistory extends AbstractAuditingEntity<Long>
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn( name = "patient_id", nullable = false)
    private Patient patient;

    @NotBlank
    @Column(name = "condition", nullable = false)
    private String condition;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 100)
    private Relations relation;

    @Column(name = "inherited_diseases")
    private Boolean inheritedDiseases;
}
