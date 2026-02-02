package com.dazzle.asklepios.domain;


import jakarta.persistence.*;
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
