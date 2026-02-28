
package com.dazzle.asklepios.service.dto.encounterVaccination;

import com.dazzle.asklepios.domain.EncounterVaccination;
import org.springframework.data.domain.Page;

import java.util.List;

public record PatientVaccineDetailsDTO(
        Page<EncounterVaccination> records,
        List<Long> brandIds,
        List<Long> doseIds
) {}
