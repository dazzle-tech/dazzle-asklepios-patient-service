package com.dazzle.asklepios.service.dto;

import com.dazzle.asklepios.domain.UserStickyNotes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserStickyNotesCreateDTO(
        @NotNull(message = "User id can not be null") long userId,
        @NotBlank(message = "Note can not be null") String note,
        @NotBlank(message = "Priority can not be null") String priority,
        @NotNull(message = "Priority order can not be null") BigDecimal priorityOrder,
        String color,
        String patientId

) implements Serializable {

    public static UserStickyNotesCreateDTO ofEntity(UserStickyNotes userStickyNotes) {
        return new UserStickyNotesCreateDTO(
                userStickyNotes.getUserId(),
                userStickyNotes.getNote(),
                userStickyNotes.getPriority(),
                userStickyNotes.getPriorityOrder(),
                userStickyNotes.getColor(),
                userStickyNotes.getPatientId() != null ? userStickyNotes.getPatientId().toString() : null
        );
    }
}
