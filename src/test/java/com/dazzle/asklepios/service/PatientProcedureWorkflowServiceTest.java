package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientProcedurePostCare;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.repository.PatientProcedureComplicationRepository;
import com.dazzle.asklepios.repository.PatientProcedureMedicationRepository;
import com.dazzle.asklepios.repository.PatientProcedurePerformanceRepository;
import com.dazzle.asklepios.repository.PatientProcedurePostCareRepository;
import com.dazzle.asklepios.repository.PatientProcedurePreAssessmentRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientProcedureServiceEquipmentRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientProcedureServiceEquipment;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import com.dazzle.asklepios.repository.PatientProcedureStaffRepository;
import com.dazzle.asklepios.repository.PatientProcedureVitalMonitoringRepository;
import com.dazzle.asklepios.domain.PatientProcedureComplication;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureComplicationDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePostCareDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureStaffDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientProcedureWorkflowServiceTest {

    private final PatientProcedureRepository procedureRepository = mock(PatientProcedureRepository.class);
    private final PatientProcedureStaffRepository staffRepository = mock(PatientProcedureStaffRepository.class);
    private final PatientProcedurePostCareRepository postCareRepository = mock(PatientProcedurePostCareRepository.class);
    private final PatientProcedureComplicationRepository complicationRepository = mock(PatientProcedureComplicationRepository.class);
    private final EncounterReopenGuard reopenGuard = mock(EncounterReopenGuard.class);
    private final PatientProcedureServiceEquipmentRepository equipmentRepository =
            mock(PatientProcedureServiceEquipmentRepository.class);
    private final PatientServiceAndProductService serviceAndProductService =
            mock(PatientServiceAndProductService.class);

    private PatientProcedureWorkflowService service;
    private PatientProcedure procedure;

    @BeforeEach
    void setUp() {
        service = new PatientProcedureWorkflowService(
                procedureRepository,
                staffRepository,
                mock(PatientProcedurePreAssessmentRepository.class),
                mock(PatientProcedurePerformanceRepository.class),
                mock(PatientProcedureMedicationRepository.class),
                complicationRepository,
                mock(PatientProcedureVitalMonitoringRepository.class),
                equipmentRepository,
                postCareRepository,
                reopenGuard,
                serviceAndProductService,
                mock(PatientServiceAndProductRepository.class)
        );
        PatientEncounter encounter = mock(PatientEncounter.class);
        when(encounter.getId()).thenReturn(7L);
        procedure = new PatientProcedure();
        procedure.setEncounter(encounter);
        procedure.setStatus(ProcStatus.REQUESTED);
        when(procedureRepository.findById(1L)).thenReturn(Optional.of(procedure));
        when(procedureRepository.save(any(PatientProcedure.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void movesThroughTheNormalStatusFlow() {
        service.changeStatus(1L, ProcStatus.AWAITING_CONSENT);
        service.changeStatus(1L, ProcStatus.STARTED);
        PatientProcedure completed = service.changeStatus(1L, ProcStatus.COMPLETED);
        assertEquals(ProcStatus.COMPLETED, completed.getStatus());
    }

    @Test
    void rejectsCompletingAProcedureThatNeverStarted() {
        assertThrows(BadRequestAlertException.class, () -> service.changeStatus(1L, ProcStatus.COMPLETED));
        assertEquals(ProcStatus.REQUESTED, procedure.getStatus());
    }

    @Test
    void rejectsLeavingWaitingPreAuthorizationManually() {
        procedure.setStatus(ProcStatus.WAITING_PRE_AUTHORIZATION);
        assertThrows(BadRequestAlertException.class, () -> service.changeStatus(1L, ProcStatus.STARTED));
    }

    @Test
    void rejectsCancellingThroughTheStatusEndpoint() {
        assertThrows(BadRequestAlertException.class, () -> service.changeStatus(1L, ProcStatus.CANCELLED));
    }

    @Test
    void rejectsWritesOnACancelledProcedure() {
        procedure.setStatus(ProcStatus.CANCELLED);
        PatientProcedureStaffDTO dto = new PatientProcedureStaffDTO(5L, null, false);
        assertThrows(BadRequestAlertException.class, () -> service.addStaff(1L, dto));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void rejectsWritesWhileTheEncounterHasAnOpenReopenSession() {
        doThrow(new BadRequestAlertException("open", "encounter", "reopen"))
                .when(reopenGuard).rejectIfOpenReopenSession(7L);
        PatientProcedureStaffDTO dto = new PatientProcedureStaffDTO(5L, null, false);
        assertThrows(BadRequestAlertException.class, () -> service.addStaff(1L, dto));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void doesNotDeleteStaffBelongingToAnotherProcedure() {
        when(staffRepository.findByIdAndPatientProcedure_Id(9L, 1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundAlertException.class, () -> service.deleteStaff(1L, 9L));
        verify(staffRepository, never()).delete(any());
    }

    @Test
    void reusesTheExistingPostCareRecord() {
        PatientProcedurePostCare existing = new PatientProcedurePostCare();
        when(postCareRepository.findByPatientProcedure_Id(1L)).thenReturn(Optional.of(existing));
        when(postCareRepository.save(existing)).thenReturn(existing);

        PatientProcedurePostCareDTO dto = mock(PatientProcedurePostCareDTO.class);
        assertEquals(existing, service.savePostCare(1L, dto));
    }

    @Test
    void addsSeveralComplicationsToOneProcedure() {
        when(complicationRepository.save(any(PatientProcedureComplication.class)))
                .thenAnswer(call -> call.getArgument(0));

        PatientProcedureComplication first = service.addComplication(
                1L, new PatientProcedureComplicationDTO("Arrhythmia", "Moderate", "Monitored"));
        PatientProcedureComplication second = service.addComplication(
                1L, new PatientProcedureComplicationDTO("Bleeding", "Mild", null));

        assertEquals("Moderate", first.getSeverity());
        assertEquals("Bleeding", second.getComplicationType());
        assertEquals(procedure, second.getPatientProcedure());
    }

    @Test
    void rejectsComplicationOnCancelledProcedure() {
        procedure.setStatus(ProcStatus.CANCELLED);

        assertThrows(BadRequestAlertException.class, () -> service.addComplication(
                1L, new PatientProcedureComplicationDTO("Arrhythmia", null, null)));

        verify(complicationRepository, never()).save(any());
    }

    @Test
    void replacesThePostCareDiagnoses() {
        PatientProcedurePostCare existing = new PatientProcedurePostCare();
        existing.getDiagnosisIcdIds().add(3L);
        when(postCareRepository.findByPatientProcedure_Id(1L)).thenReturn(Optional.of(existing));
        when(postCareRepository.save(existing)).thenReturn(existing);
        PatientProcedurePostCareDTO dto = mock(PatientProcedurePostCareDTO.class);
        when(dto.diagnosisIcdIds()).thenReturn(Set.of(5L, 9L));

        assertEquals(Set.of(5L, 9L), service.savePostCare(1L, dto).getDiagnosisIcdIds());
    }

    @Test
    void equipmentIsBilledAsAnEncounterServiceOfTheProcedurePatient() {
        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(30L);
        procedure.setPatient(patient);
        PatientServiceAndProduct created = new PatientServiceAndProduct();
        created.setId(55L);
        when(serviceAndProductService.create(any())).thenReturn(created);

        service.addEquipment(1L, new PatientServiceProductCreateDTO(
                999L, 888L, BillingItemTypes.SERVICE, null, null, 12L, null,
                2L, BigDecimal.TEN, Currency.values()[0], ServiceSource.PROCEDURE, 77L,
                null, false, null, null));

        ArgumentCaptor<PatientServiceProductCreateDTO> sent = ArgumentCaptor.forClass(PatientServiceProductCreateDTO.class);
        verify(serviceAndProductService).create(sent.capture());
        assertEquals(30L, sent.getValue().patientId());
        assertEquals(7L, sent.getValue().encounterId());
        assertEquals(ServiceSource.SERVICE_AND_PRODUCT, sent.getValue().serviceSource());
        assertEquals(null, sent.getValue().sourceId());
        ArgumentCaptor<PatientProcedureServiceEquipment> link = ArgumentCaptor.forClass(PatientProcedureServiceEquipment.class);
        verify(equipmentRepository).save(link.capture());
        assertEquals(55L, link.getValue().getServiceProductId());
    }

    @Test
    void doesNotCancelAnItemThatBelongsToAnotherProcedure() {
        when(equipmentRepository.findByPatientProcedure_IdAndServiceProductId(1L, 55L)).thenReturn(Optional.empty());

        assertThrows(NotFoundAlertException.class, () -> service.deleteEquipment(1L, 55L));

        verify(serviceAndProductService, never()).remove(any());
    }
}
