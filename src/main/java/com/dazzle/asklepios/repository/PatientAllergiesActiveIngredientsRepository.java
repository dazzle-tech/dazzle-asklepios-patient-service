package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientAllergies;
import com.dazzle.asklepios.domain.PatientAllergiesActiveIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PatientAllergiesActiveIngredientsRepository extends JpaRepository<PatientAllergiesActiveIngredient, Long>, JpaSpecificationExecutor<PatientAllergiesActiveIngredient> {

    List<PatientAllergiesActiveIngredient> findByPatientAllergyId(Long patientAllergyId);
    void deleteByPatientAllergy(PatientAllergies patientAllergy);
    List<PatientAllergiesActiveIngredient> findByPatientAllergy(PatientAllergies patientAllergy);


}
