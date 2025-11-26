package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.FamilyMemberCategory;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "is_active")
    private Boolean isActive = true;
}
