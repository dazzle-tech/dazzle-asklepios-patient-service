package com.dazzle.asklepios.web.rest.vm.PatientAllergies;



import com.dazzle.asklepios.domain.PatientAllergiesActiveIngredient;
import java.io.Serializable;
import java.time.Instant;

public record PatientAllergiesActiveIngredientResponseVM(

        Long id,
        Long activeIngredientId,
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate

) implements Serializable {

    public static PatientAllergiesActiveIngredientResponseVM ofEntity(
            PatientAllergiesActiveIngredient entity
    ) {
        return new PatientAllergiesActiveIngredientResponseVM(
                entity.getId(),
                entity.getActiveIngredientId(),
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}
