package com.dazzle.asklepios.service.dto.activitySetup;

import com.dazzle.asklepios.domain.enumeration.ActivityDueUnit;
import com.dazzle.asklepios.domain.enumeration.ActivityTemplateKind;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ActivityTemplateDTO {

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

    @NotBlank
    private String categoryCode;

    @NotNull
    private ActivityTemplateKind kind;

    private PatientActivityPriority defaultPriority;

    @Min(0)
    private Integer dueOffset;

    private ActivityDueUnit dueUnit;

    private List<String> assigneeRoles;

    private List<String> creatorRoles;

    private List<@Size(max = 100) String> outcomeOptions;

    private List<@Size(max = 200) String> checklist;

    private Boolean requireOutcome;

    private Integer viewOrder;

    private Boolean isActive;
}
