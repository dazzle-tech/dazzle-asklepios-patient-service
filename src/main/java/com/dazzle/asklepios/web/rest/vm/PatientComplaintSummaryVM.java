package com.dazzle.asklepios.web.rest.vm;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PatientComplaintSummaryVM {

    private long newCount;
    private long unassignedCount;
    private long assignedToMeCount;
    private long overdueCount;
    private long resolvedCount;
}
