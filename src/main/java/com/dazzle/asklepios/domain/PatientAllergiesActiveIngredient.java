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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

@Entity
@Table(
        name = "patient_allergies_active_ingredients"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientAllergiesActiveIngredient extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_allergies_id", nullable = false)
    private PatientAllergies patientAllergy;

    @Column(name = "active_ingredient_id", nullable = false)
    private Long activeIngredientId;

}
