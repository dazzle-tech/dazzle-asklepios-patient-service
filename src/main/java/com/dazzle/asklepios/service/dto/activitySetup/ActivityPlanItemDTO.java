package com.dazzle.asklepios.service.dto.activitySetup;

import com.dazzle.asklepios.domain.enumeration.ActivityDueUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivityPlanItemDTO {

    @NotBlank
    private String templateCode;

    @Min(0)
    private Integer dueOffset;

    private ActivityDueUnit dueUnit;

    @Size(max = 2000)
    private String note;
}
