//package com.dazzle.asklepios.integration.waseel.dto.approval;
//
//import com.dazzle.asklepios.domain.PatientEncounter;
//import com.dazzle.asklepios.domain.enumeration.EncounterType;
//import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDate;
//
//@Component
//public class ApprovalEncounterMapper {
//
//    public WaseelApprovalEncounter toWaseelEncounter(PatientEncounter encounter, String providerId) {
//        if (encounter == null) {
//            throw new BadRequestAlertException(
//                    "Encounter is required",
//                    "preAuthorization",
//                    "encounter.required"
//            );
//        }
//
//        return new WaseelApprovalEncounter(
//                "planned",
//                resolveEncounterClass(encounter.getEncounterType()),
//                "acute-care",
//                encounter.getEncounterDate() != null ? encounter.getEncounterDate() : LocalDate.now(),
//                "ICSE",
//                toLong(providerId),
//                null,
//                null
//        );
//    }
//
//    private String resolveEncounterClass(EncounterType encounterType) {
//        if (encounterType == null) {
//            return "AMB";
//        }
//
//        return switch (encounterType) {
//            case EMERGENCY -> "EMER";
//            case INPATIENT -> "IMP";
//            case DAYCASE -> "DC";
//            case CLINIC -> "AMB";
//        };
//    }
//
//    private Long toLong(String value) {
//        if (value == null || value.isBlank()) {
//            return null;
//        }
//
//        return Long.valueOf(value.trim());
//    }
//}

package com.dazzle.asklepios.integration.waseel.dto.approval;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ApprovalEncounterMapper {

    public WaseelApprovalEncounter toWaseelEncounter(PatientEncounter encounter, String providerId) {
        if (encounter == null) {
            throw new BadRequestAlertException(
                    "Encounter is required",
                    "preAuthorization",
                    "encounter.required"
            );
        }

        return new WaseelApprovalEncounter(
                "planned",                                           // status
                "HH",                                                // encounterClass
                "acute-care",                                        // serviceType
                encounter.getEncounterDate() != null
                        ? encounter.getEncounterDate()
                        : LocalDate.now(),                           // startDate
                "ICSE",                                              // serviceEventType
                toLong(providerId),                                  // serviceProvider
                toLong(providerId),                                  // facility
                null,                                                // periodEnd
                ""                                                   // causeOfDeath
        );
    }

    private Long toLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Long.valueOf(value.trim());
    }
}