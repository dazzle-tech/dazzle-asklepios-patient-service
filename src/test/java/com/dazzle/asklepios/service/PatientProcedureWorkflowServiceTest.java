package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientProcedurePostCare;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.repository.PatientProcedureMedicationRepository;
import com.dazzle.asklepios.repository.PatientProcedurePerformanceRepository;
import com.dazzle.asklepios.repository.PatientProcedurePostCareRepository;
import com.dazzle.asklepios.repository.PatientProcedurePreAssessmentRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientProcedureServiceEquipmentRepository;
import com.dazzle.asklepios.repository.PatientProcedureStaffRepository;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePostCareDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureStaffDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
    private final EncounterReopenGuard reopenGuard = mock(EncounterReopenGuard.class);

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
                mock(PatientProcedureServiceEquipmentRepository.class),
                postCareRepository,
                reopenGuard
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
}
