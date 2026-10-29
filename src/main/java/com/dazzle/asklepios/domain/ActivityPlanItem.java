package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ActivityDueUnit;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One task of an activity plan. Priority and team always come from the task template;
 * the plan only decides when the task is due.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityPlanItem implements Serializable {

    private String templateCode;

    /** Counted from the moment the plan is applied; empty uses the template due rule. */
    private Integer dueOffset;

    private ActivityDueUnit dueUnit;

    private String note;
}
