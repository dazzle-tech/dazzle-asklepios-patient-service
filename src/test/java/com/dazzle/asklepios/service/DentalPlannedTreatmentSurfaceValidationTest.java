package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentStatusLogRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.service.helper.CDTCodeHelper;
import com.dazzle.asklepios.service.helper.DentalActionHelper;
import com.dazzle.asklepios.service.helper.ProcedureHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentSaveDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DentalPlannedTreatmentSurfaceValidationTest {

    private static final Long PROCEDURE_ID = 5L;
    private static final String NOT_RELATED = "surfaceNotRelatedToProcedure";

    private ProcedureHelper procedureHelper;
    private DentalPlannedTreatmentService service;

    @BeforeEach
    void setUp() {
        procedureHelper = mock(ProcedureHelper.class);
        service = new DentalPlannedTreatmentService(
                mock(DentalPlannedTreatmentRepository.class),
                mock(DentalChartToothRepository.class),
                mock(DentalChartRepository.class),
                mock(DentalChartService.class),
                mock(PatientEncounterRepository.class),
                mock(ToothServiceRepository.class),
                mock(ToothActionRepository.class),
                mock(ToothCdtRepository.class),
                mock(DentalActionHelper.class),
                mock(DentalVisitNumberService.class),
                mock(PlannedTreatmentChartEffectService.class),
                mock(ServiceHelper.class),
                mock(DentalTreatmentPlanService.class),
                mock(CDTCodeHelper.class),
                procedureHelper,
                mock(DentalPlannedTreatmentStatusLogRepository.class),
                mock(DentalPlannedTreatmentBillingService.class)
        );
    }

    @Test
    void surfaceOutsideTheProcedureConfigurationIsRejected() {
        procedureConfiguredWith(List.of("MESIAL", "OCCLUSAL"));

        assertThat(errorKeyOfCreate(ToothSurface.MESIAL, ToothSurface.DISTAL)).isEqualTo(NOT_RELATED);
    }

    @Test
    void surfacesInsideTheProcedureConfigurationAreAccepted() {
        procedureConfiguredWith(List.of("MESIAL", "OCCLUSAL"));

        assertThat(errorKeyOfCreate(ToothSurface.MESIAL, ToothSurface.OCCLUSAL)).isNotEqualTo(NOT_RELATED);
    }

    @Test
    void procedureWithoutConfigurationIsNotRestricted() {
        procedureConfiguredWith(List.of());

        assertThat(errorKeyOfCreate(ToothSurface.DISTAL)).isNotEqualTo(NOT_RELATED);
    }

    @Test
    void procedureWithNullConfigurationIsNotRestricted() {
        procedureConfiguredWith(null);

        assertThat(errorKeyOfCreate(ToothSurface.DISTAL)).isNotEqualTo(NOT_RELATED);
    }

    @Test
    void emptySelectionStillRequiresASurface() {
        procedureConfiguredWith(List.of("MESIAL"));

        assertThat(errorKeyOfCreate()).isEqualTo("surfaceRequired");
    }

    private void procedureConfiguredWith(List<String> relatedSurfaces) {
        when(procedureHelper.getProcedure(PROCEDURE_ID)).thenReturn(new ProcedureSetupDTO(
                PROCEDURE_ID, "Filling", "97531-00-00", "DENTAL_PROCEDURE", true, null, null, null, null,
                true, 1L, null, null, null, relatedSurfaces));
    }

    /** Runs a create and returns the validation key it fails with, or null if it got past validation. */
    private String errorKeyOfCreate(ToothSurface... surfaces) {
        PlannedTreatmentSaveDTO dto = new PlannedTreatmentSaveDTO(
                1L, 2L, PlannedTreatmentType.CURRENT, null, null, 3L, null, null, null,
                List.of(surfaces), null, null, PROCEDURE_ID, null, null, null, null,
                null, null, null, null, null, null, null);
        try {
            service.createPlannedTreatment(dto);
        } catch (BadRequestAlertException ex) {
            return ex.getErrorKey();
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }
}
