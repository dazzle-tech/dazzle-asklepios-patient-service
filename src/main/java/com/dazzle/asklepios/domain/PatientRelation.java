package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.FamilyMemberCategory;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patient_relation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientRelation extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "relative_patient_id", nullable = false)
    private Patient relativePatient;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 50)
    private RelationType relationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", length = 20)
    private FamilyMemberCategory categoryType;

}
