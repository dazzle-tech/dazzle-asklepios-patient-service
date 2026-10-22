package com.dazzle.asklepios.integration.waseel.service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalDiagnosis;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovalDiagnosisMapperTest {

    @Test
    void claimSendsEveryVisitDiagnosisWithThePrimaryFirstAsPrincipal() {
        List<WaseelApprovalDiagnosis> sent = ApprovalDiagnosisMapper.withSinglePrincipal(List.of(
                diagnosis(1, "secondary", "E55.9"),
                diagnosis(2, "principal", "E88.8"),
                diagnosis(3, "secondary", "E03.9")
        ));

        assertEquals(3, sent.size());
        assertEquals(1, sent.get(0).sequence());
        assertEquals("principal", sent.get(0).type());
        assertEquals("E88.8", sent.get(0).diagnosisCode());
        assertEquals(2, sent.get(1).sequence());
        assertEquals("secondary", sent.get(1).type());
        assertEquals("E55.9", sent.get(1).diagnosisCode());
        assertEquals(3, sent.get(2).sequence());
        assertEquals("secondary", sent.get(2).type());
        assertEquals("E03.9", sent.get(2).diagnosisCode());
    }

    @Test
    void extraPrincipalsBecomeSecondary() {
        List<WaseelApprovalDiagnosis> sent = ApprovalDiagnosisMapper.withSinglePrincipal(List.of(
                diagnosis(1, "principal", "E88.8"),
                diagnosis(2, "principal", "E03.9")
        ));

        assertEquals(2, sent.size());
        assertEquals("principal", sent.get(0).type());
        assertEquals("E88.8", sent.get(0).diagnosisCode());
        assertEquals("secondary", sent.get(1).type());
        assertEquals("E03.9", sent.get(1).diagnosisCode());
    }

    @Test
    void claimUsesTheFirstDiagnosisAsPrincipalWhenTheVisitHasNoPrimary() {
        List<WaseelApprovalDiagnosis> sent = ApprovalDiagnosisMapper.withSinglePrincipal(List.of(
                diagnosis(4, "secondary", "E55.9"),
                diagnosis(5, "secondary", "E03.9")
        ));

        assertEquals(2, sent.size());
        assertEquals("principal", sent.get(0).type());
        assertEquals("E55.9", sent.get(0).diagnosisCode());
        assertEquals("secondary", sent.get(1).type());
        assertEquals("E03.9", sent.get(1).diagnosisCode());
    }

    @Test
    void emptyVisitSendsNoDiagnosis() {
        assertTrue(ApprovalDiagnosisMapper.withSinglePrincipal(List.of()).isEmpty());
    }

    private static WaseelApprovalDiagnosis diagnosis(int sequence, String type, String code) {
        return new WaseelApprovalDiagnosis(sequence, code, type, code);
    }
}
