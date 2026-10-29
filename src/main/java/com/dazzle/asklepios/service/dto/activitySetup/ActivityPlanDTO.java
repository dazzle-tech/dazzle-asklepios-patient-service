package com.dazzle.asklepios.service.dto.activitySetup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ActivityPlanDTO {

    /** Fixed after creation. */
    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code may only hold capital letters, digits and underscores.")
    private String code;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 500)
    private String description;

    @NotEmpty
    private List<@Valid ActivityPlanItemDTO> items;

    private Integer viewOrder;

    private Boolean isActive;
}
