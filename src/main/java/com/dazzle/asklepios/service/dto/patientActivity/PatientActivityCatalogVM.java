package com.dazzle.asklepios.service.dto.patientActivity;

import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.PatientActivityType;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public class PatientActivityCatalogVM {

    private final List<PatientActivityCategory> categories;
    private final List<PatientActivityTypeVM> types;
    private final List<PatientActivityStatus> statuses;
    private final List<PatientActivityStatus> creatableStatuses;
    private final List<PatientActivityPriority> priorities;

    private PatientActivityCatalogVM(
            List<PatientActivityCategory> categories,
            List<PatientActivityTypeVM> types,
            List<PatientActivityStatus> statuses,
            List<PatientActivityStatus> creatableStatuses,
            List<PatientActivityPriority> priorities
    ) {
        this.categories = categories;
        this.types = types;
        this.statuses = statuses;
        this.creatableStatuses = creatableStatuses;
        this.priorities = priorities;
    }

    public static PatientActivityCatalogVM fromEnums() {
        List<PatientActivityTypeVM> types = Arrays.stream(PatientActivityType.values())
                .map(type -> new PatientActivityTypeVM(
                        type.name(),
                        type.getCategory(),
                        type.defaultStatus(),
                        type.defaultPriority()
                ))
                .toList();

        return new PatientActivityCatalogVM(
                List.of(PatientActivityCategory.values()),
                types,
                List.of(PatientActivityStatus.values()),
                List.of(PatientActivityStatus.OPEN, PatientActivityStatus.DONE),
                List.of(PatientActivityPriority.values())
        );
    }
}
