package com.dazzle.asklepios.web.rest.vm.patientDashboard;

import java.io.Serializable;
import java.util.List;

public record EncounterDashboardPreferenceVM(
    Long userId,
    Long encounterId,
    boolean customized,
    List<String> widgetKeys
) implements Serializable {
}
