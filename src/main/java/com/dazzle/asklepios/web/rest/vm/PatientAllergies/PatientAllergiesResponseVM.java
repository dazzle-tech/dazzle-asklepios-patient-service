package com.dazzle.asklepios.web.rest.vm.PatientAllergies;


import com.dazzle.asklepios.domain.PatientAllergies;
import com.dazzle.asklepios.repository.PatientAllergiesActiveIngredientsRepository;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record PatientAllergiesResponseVM(
        Long id,
        Long patientId,
        Long encounterId,
        String allergenType,
        Long allergenId,
        String severity,
        Long medicationClassId,
        String criticality,
        String certainty,
        String treatmentStrategy,
        String onset,
        boolean onsetDateUndefined,
        Instant onsetDate,
        String typeOfPropensity,
        boolean byPatient,
        String sourceOfInformation,
        String note,
        String allergicReactions,
        String status,
        String resolvedBy,
        Instant resolvedDate,
        String cancelledBy,
        Instant cancelledDate,
        String cancellationReason,
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate,
        List<PatientAllergiesActiveIngredientResponseVM> activeIngredients
) implements Serializable {


    public static PatientAllergiesResponseVM ofEntity(
            PatientAllergies entity,
            PatientAllergiesActiveIngredientsRepository activeIngredientRepository
    ) {

        List<PatientAllergiesActiveIngredientResponseVM> activeIngredients =
                activeIngredientRepository
                        .findByPatientAllergyId(entity.getId())
                        .stream()
                        .map(PatientAllergiesActiveIngredientResponseVM::ofEntity)
                        .toList();

        return ofEntity(entity, activeIngredients);
    }

    private static PatientAllergiesResponseVM ofEntity(
            PatientAllergies entity,
            List<PatientAllergiesActiveIngredientResponseVM> activeIngredients
    ) {
        return new PatientAllergiesResponseVM(
                entity.getId(),
                entity.getPatientId(),
                entity.getEncounterId(),
                entity.getAllergenType() != null ? entity.getAllergenType().name() : null,
                entity.getAllergenId(),
                entity.getSeverity() != null ? entity.getSeverity().name() : null,
                entity.getMedicationClassId(),
                entity.getCriticality(),
                entity.getCertainty(),
                entity.getTreatmentStrategy(),
                entity.getOnset(),
                entity.isOnsetDateUndefined(),
                entity.getOnsetDate(),
                entity.getTypeOfPropensity(),
                entity.isByPatient(),
                entity.getSourceOfInformation(),
                entity.getNote(),
                entity.getAllergicReactions(),
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getResolvedBy(),
                entity.getResolvedDate(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason(),
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate(),
                activeIngredients
        );
    }
}
