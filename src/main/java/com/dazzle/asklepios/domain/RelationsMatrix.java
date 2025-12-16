package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "relations_matrix")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationsMatrix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_patient_gender", nullable = false)
    private Gender firstPatientGender;

    @Enumerated(EnumType.STRING)
    @Column(name = "second_patient_gender", nullable = false)
    private Gender secondPatientGender;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_relation_code", nullable = false)
    private RelationType firstRelationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "second_relation_code", nullable = false)
    private RelationType secondRelationCode;
}
