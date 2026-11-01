package com.dazzle.asklepios.service.dto.activitySetup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivityCategoryDTO {

    /** Fixed after creation. */
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code may only hold capital letters, digits and underscores.")
    private String code;

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 50)
    private String icon;

    @Size(max = 20)
    private String color;

    private Integer viewOrder;

    private Boolean isActive;
}
