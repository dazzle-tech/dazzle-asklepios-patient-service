package com.dazzle.asklepios.service.dailybriefing;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingRefund;
import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.Department;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.EncounterAssignToBed;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientInsuranceBenefitRule;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.BillingRefundRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.DailyBriefingAppointmentSpecification;
import com.dazzle.asklepios.repository.DailyBriefingBedSpecification;
import com.dazzle.asklepios.repository.DailyBriefingBillingSpecification;
import com.dazzle.asklepios.repository.DailyBriefingClaimSpecification;
import com.dazzle.asklepios.repository.DailyBriefingEncounterSpecification;
import com.dazzle.asklepios.repository.DepartmentRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.EncounterAssignToBedRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceBenefitRuleRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingCasesResponse.DailyBriefingCase;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Drill;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyBriefingQueryGateway {

    private static final Logger log = LoggerFactory.getLogger(DailyBriefingQueryGateway.class);
    static final int MAX_ROWS = 20000;

    private static final List<TreatmentStatus> COMPLETED = List.of(TreatmentStatus.COMPLETED, TreatmentStatus.CLOSED, TreatmentStatus.DISCHARGED);
    private static final List<TreatmentStatus> NOT_OPEN = List.of(TreatmentStatus.COMPLETED, TreatmentStatus.CLOSED, TreatmentStatus.DISCHARGED, TreatmentStatus.CANCELLED);
    private static final List<BillingChargeStatus> INACTIVE_CHARGES = List.of(BillingChargeStatus.CANCELLED, BillingChargeStatus.REVERSED);
    private static final List<BillingRefundStatus> REFUND_DONE = List.of(BillingRefundStatus.COMPLETED, BillingRefundStatus.PARTIALLY_COMPLETED);
    private static final List<AppointmentStatus> OFF_BOOK = List.of(AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED);
    private static final List<DiagnosticStatus> ORDER_DONE = List.of(DiagnosticStatus.RESULT_APPROVED, DiagnosticStatus.CANCELLED, DiagnosticStatus.REJECTED, DiagnosticStatus.COMPLETED);
    private static final List<ClaimStatus> SUBMITTED = List.of(ClaimStatus.SUBMITTED, ClaimStatus.ACCEPTED, ClaimStatus.REJECTED, ClaimStatus.FAILED);
    private static final List<TreatmentStatus> IN_DEPARTMENT = List.of(TreatmentStatus.ONGOING, TreatmentStatus.ASSIGNED_TO_BED);
    private static final List<TreatmentStatus> IN_TRIAGE = List.of(TreatmentStatus.WAITING_TRIAGE, TreatmentStatus.TRIAGE_STARTED);

    private final PatientEncounterRepository patientEncounterRepository;
    private final AppointmentRepository appointmentRepository;
    private final DepartmentRepository departmentRepository;
    private final BillingChargeRepository billingChargeRepository;
    private final BillingPaymentRepository billingPaymentRepository;
    private final BillingRefundRepository billingRefundRepository;
    private final BillingChargeResponsibilityRepository billingChargeResponsibilityRepository;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientInsuranceBenefitRuleRepository patientInsuranceBenefitRuleRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final EncounterAssignToBedRepository encounterAssignToBedRepository;

    public Set<Long> practitionerIds(Long facilityId, Long departmentId, LocalDate fromDay, LocalDate toDay, Instant from, Instant to) {
        BriefingScope encounterScope = new BriefingScope(facilityId, departmentId, null, null, false);
        BriefingScope appointmentScope = new BriefingScope(facilityId, departmentId, null, null, false);
        Set<Long> ids = new LinkedHashSet<>();
        patientEncounterRepository.findAll(
                DailyBriefingEncounterSpecification.forScope(encounterScope)
                        .and(DailyBriefingEncounterSpecification.encounterDateBetween(fromDay, toDay))
                        .and(DailyBriefingEncounterSpecification.practitionerPresent())
        ).forEach(encounter -> ids.add(encounter.getPractitionerId()));
        appointmentRepository.findAll(
                DailyBriefingAppointmentSpecification.forScope(appointmentScope)
                        .and(DailyBriefingAppointmentSpecification.startBetween(from, to))
                        .and(DailyBriefingAppointmentSpecification.defaultPractitionerPresent())
        ).forEach(appointment -> ids.add(appointment.getDefaultPractitionerId()));
        appointmentRepository.findAll(
                DailyBriefingAppointmentSpecification.forScope(appointmentScope)
                        .and(DailyBriefingAppointmentSpecification.startBetween(from, to))
                        .and(DailyBriefingAppointmentSpecification.resourceIsPractitioner())
        ).forEach(appointment -> ids.add(appointment.getResourceId()));
        ids.remove(null);
        return ids;
    }

    public DailyBriefingFacts load(BriefingScope scope, BriefingWindow window) {
        DailyBriefingFacts.Builder builder = DailyBriefingFacts.builder();
        if (scope.matchNone()) {
            Boolean module = read("urgentCare.module", () -> hasUrgentCareModule(scope.facilityId()), builder);
            builder.urgentCare(module != null && module
                    ? new DailyBriefingFacts.UrgentCareSnapshot(true, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, 0, 0, null, 0, 0)
                    : new DailyBriefingFacts.UrgentCareSnapshot(module, null, null, null, null, null, null, null, null, null, null, null, null, null));
            return builder.build();
        }
        loadEncounters(scope, window, builder);
        loadAppointments(scope, window, builder);
        loadBilling(scope, window, builder);
        loadInsurance(scope, window, builder);
        loadOrders(scope, window, builder);
        builder.urgentCare(loadUrgentCare(scope, window, builder));
        builder.comparison(loadComparison(scope, window, builder));
        return builder.build();
    }

    public List<DailyBriefingCase> cases(Drill drill, BriefingScope scope, BriefingWindow window, int limit) {
        int fetch = limit + 1;
        List<DailyBriefingCase> rows = switch (drill) {
            case COMPLETED_ENCOUNTERS -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.statusIn(COMPLETED)), fetch);
            case CANCELLED_ENCOUNTERS -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.statusEquals(TreatmentStatus.CANCELLED)), fetch);
            case INCOMPLETE_ENCOUNTERS -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.statusNotIn(NOT_OPEN)), fetch);
            case MISSING_CHIEF_COMPLAINT -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.statusIn(COMPLETED))
                    .and(DailyBriefingEncounterSpecification.missingChiefComplaint()), fetch);
            case UNBILLED_ENCOUNTERS -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.statusIn(COMPLETED))
                    .and(DailyBriefingEncounterSpecification.billingStatus(EncounterBillingStatus.OPEN)), fetch);
            case OPEN_ENCOUNTERS -> encounterCases(scope, DailyBriefingEncounterSpecification.statusNotIn(NOT_OPEN)
                    .and(DailyBriefingEncounterSpecification.openOlderThan(
                            window.refreshedAt().minusSeconds(window.openEncounterHours() * 3600L),
                            window.currentDay().minusDays(1))), fetch);
            case URGENT_CARE_ADMISSIONS -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY)
                    .and(DailyBriefingEncounterSpecification.hasBedAssignBetween(window.previousStart(), window.previousEnd())), fetch);
            case URGENT_CARE_VISITS -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY)), fetch);
            case URGENT_CARE_WAITING_LIST -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                    .and(DailyBriefingEncounterSpecification.statusEquals(TreatmentStatus.WAITING_LIST)), fetch);
            case URGENT_CARE_TRIAGE -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterDate(window.previousDay())
                    .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                    .and(DailyBriefingEncounterSpecification.statusIn(IN_TRIAGE)), fetch);
            case URGENT_CARE_IN_DEPARTMENT -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY)
                    .and(DailyBriefingEncounterSpecification.statusIn(IN_DEPARTMENT)), fetch);
            case URGENT_CARE_DISCHARGES -> encounterCases(scope, DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY)
                    .and(DailyBriefingEncounterSpecification.dischargeBetween(
                            LocalDateTime.ofInstant(window.previousStart(), window.zone()),
                            LocalDateTime.ofInstant(window.previousEnd(), window.zone()))), fetch);
            case TODAY_APPOINTMENTS -> appointmentCases(scope, DailyBriefingAppointmentSpecification.patientPresent(), window.currentStart(), window.currentEnd(), fetch);
            case PREVIOUS_CANCELLATIONS -> appointmentCases(scope, DailyBriefingAppointmentSpecification.patientPresent()
                    .and(DailyBriefingAppointmentSpecification.statusEquals(AppointmentStatus.CANCELLED)), window.previousStart(), window.previousEnd(), fetch);
            case PREVIOUS_NO_SHOWS -> appointmentCases(scope, DailyBriefingAppointmentSpecification.patientPresent()
                    .and(DailyBriefingAppointmentSpecification.statusEquals(AppointmentStatus.NO_SHOW)), window.previousStart(), window.previousEnd(), fetch);
            case BOOKED_WITHOUT_PRACTITIONER -> appointmentCases(scope, DailyBriefingAppointmentSpecification.bookedWithoutPractitioner()
                    .and(DailyBriefingAppointmentSpecification.statusNotIn(OFF_BOOK)), window.currentStart(), window.currentEnd(), fetch);
            case SCHEDULE_CONFLICTS -> conflictCases(scope, window, fetch);
            case PENDING_AUTHORIZATIONS, AUTHORIZATION_DENIALS -> authorizationCases(scope, drill, fetch);
            case SERVICES_ON_HOLD -> holdCases(scope, fetch);
            case READY_CLAIMS -> claimCases(scope, ClaimStatus.DRAFT, fetch);
            case CLAIM_DENIALS -> claimCases(scope, ClaimStatus.REJECTED, fetch);
            case CLAIM_FAILURES -> claimCases(scope, ClaimStatus.FAILED, fetch);
            case OVERDUE_ORDERS -> orderCases(scope, window, fetch);
        };
        if (rows.size() > limit) {
            return rows.subList(0, limit);
        }
        return rows;
    }

    private void loadEncounters(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        Specification<PatientEncounter> previous = DailyBriefingEncounterSpecification.forScope(scope)
                .and(DailyBriefingEncounterSpecification.encounterDate(window.previousDay()));
        Long total = read("encounters.previous", () -> patientEncounterRepository.count(previous), builder);
        if (total == null) {
            builder.previousEncounters(null);
        } else if (total > MAX_ROWS) {
            builder.previousEncountersCapped(true, total);
        } else {
            builder.previousEncounters(read("encounters.previous.rows", () -> encounterRows(previous), builder));
        }
        Specification<PatientEncounter> open = DailyBriefingEncounterSpecification.forScope(scope)
                .and(DailyBriefingEncounterSpecification.statusNotIn(NOT_OPEN));
        Long openTotal = read("encounters.open", () -> patientEncounterRepository.count(open), builder);
        if (openTotal == null) {
            builder.openEncounters(null);
        } else if (openTotal > MAX_ROWS) {
            builder.openEncountersCapped(true, openTotal);
        } else {
            builder.openEncounters(read("encounters.open.rows", () -> encounterRows(open), builder));
        }
    }

    private void loadAppointments(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        builder.currentAppointmentStatuses(read("appointments.current", () -> statusCounts(scope, window.currentStart(), window.currentEnd()), builder));
        builder.previousAppointmentStatuses(read("appointments.previous", () -> statusCounts(scope, window.previousStart(), window.previousEnd()), builder));
        builder.currentAppointmentsByDepartment(read("appointments.department", () -> departmentCounts(scope, window.currentStart(), window.currentEnd()), builder));
        builder.currentAppointmentsByService(read("appointments.service", () -> serviceCounts(scope, window.currentStart(), window.currentEnd()), builder));
        builder.currentOpenSlots(read("appointments.slots", () -> appointmentRepository.count(
                DailyBriefingAppointmentSpecification.forScope(scope)
                        .and(DailyBriefingAppointmentSpecification.patientAbsent())
                        .and(DailyBriefingAppointmentSpecification.statusEquals(AppointmentStatus.NEW))
                        .and(DailyBriefingAppointmentSpecification.startBetween(window.currentStart(), window.currentEnd()))
        ), builder));
        builder.bookedWithoutPractitioner(read("appointments.missingPractitioner", () -> appointmentRepository.count(
                DailyBriefingAppointmentSpecification.forScope(scope)
                        .and(DailyBriefingAppointmentSpecification.bookedWithoutPractitioner())
                        .and(DailyBriefingAppointmentSpecification.statusNotIn(OFF_BOOK))
                        .and(DailyBriefingAppointmentSpecification.startBetween(window.currentStart(), window.currentEnd()))
        ), builder));
        Specification<Appointment> bookedSpec = DailyBriefingAppointmentSpecification.forScope(scope)
                .and(DailyBriefingAppointmentSpecification.patientPresent())
                .and(DailyBriefingAppointmentSpecification.startBetween(window.currentStart(), window.currentEnd()));
        Long booked = read("appointments.bookedCount", () -> appointmentRepository.count(bookedSpec), builder);
        if (booked == null) {
            builder.currentBooked(null);
        } else if (booked > MAX_ROWS) {
            builder.currentBookedCapped(true);
        } else {
            builder.currentBooked(read("appointments.booked", () -> appointmentRows(bookedSpec), builder));
        }
    }

    private void loadBilling(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        builder.billing(new DailyBriefingFacts.BillingSnapshot(
                read("billing.charges", () -> chargeRows(scope, window.previousStart(), window.previousEnd()), builder),
                read("billing.cancellations", () -> moneyRows(billingChargeRepository.findAll(
                        DailyBriefingBillingSpecification.chargesForScope(scope)
                                .and(DailyBriefingBillingSpecification.cancelledDateBetween(window.previousStart(), window.previousEnd()))
                                .and(DailyBriefingBillingSpecification.statusIn(INACTIVE_CHARGES))
                ), charge -> charge.getCurrency() == null ? null : charge.getCurrency().name(), BillingCharge::getNetAmount, ignored -> null), builder),
                read("billing.reversedUntimed", () -> billingChargeRepository.count(
                        DailyBriefingBillingSpecification.chargesForScope(scope)
                                .and(DailyBriefingBillingSpecification.statusEquals(BillingChargeStatus.REVERSED))
                                .and(DailyBriefingBillingSpecification.cancelledDateIsNull())
                                .and(DailyBriefingBillingSpecification.chargeDateBetween(window.previousStart(), window.previousEnd()))
                ), builder),
                read("billing.payments", () -> moneyRows(billingPaymentRepository.findAll(
                        DailyBriefingBillingSpecification.paymentsForScope(scope)
                                .and(DailyBriefingBillingSpecification.paymentStatus(BillingPaymentStatus.COMPLETED))
                                .and(DailyBriefingBillingSpecification.paymentDateBetween(window.previousStart(), window.previousEnd()))
                ), payment -> payment.getCurrency() == null ? null : payment.getCurrency().name(), BillingPayment::getAmount,
                        payment -> payment.getPayerType() == null ? null : payment.getPayerType().name()), builder),
                read("billing.refunds", () -> moneyRows(billingRefundRepository.findAll(
                        DailyBriefingBillingSpecification.refundsForScope(scope)
                                .and(DailyBriefingBillingSpecification.refundStatusIn(REFUND_DONE))
                                .and(DailyBriefingBillingSpecification.refundCompletedBetween(window.previousStart(), window.previousEnd()))
                                .and(DailyBriefingBillingSpecification.withOriginalPayment())
                ), refund -> refund.getOriginalPayment().getCurrency() == null ? null : refund.getOriginalPayment().getCurrency().name(),
                        BillingRefund::getRefundedAmount,
                        refund -> refund.getOriginalPayment().getPayerType() == null ? null : refund.getOriginalPayment().getPayerType().name()), builder),
                read("billing.unclassifiedRefunds", () -> billingRefundRepository.count(
                        DailyBriefingBillingSpecification.refundsForScope(scope)
                                .and(DailyBriefingBillingSpecification.refundStatusIn(REFUND_DONE))
                                .and(DailyBriefingBillingSpecification.refundCompletedBetween(window.previousStart(), window.previousEnd()))
                                .and(DailyBriefingBillingSpecification.originalPaymentAbsent())
                ), builder),
                read("billing.responsibility", () -> moneyRows(billingChargeResponsibilityRepository.findAll(
                        DailyBriefingBillingSpecification.responsibilitiesForScope(scope)
                                .and(DailyBriefingBillingSpecification.activeResponsibility(INACTIVE_CHARGES))
                                .and(DailyBriefingBillingSpecification.responsibilityChargeDateBetween(window.previousStart(), window.previousEnd()))
                ), responsibility -> responsibility.getCurrency() == null ? null : responsibility.getCurrency().name(),
                        BillingChargeResponsibility::getResponsibilityAmount,
                        responsibility -> responsibility.getResponsiblePartyType() == null ? null : responsibility.getResponsiblePartyType().name()), builder),
                read("billing.invoices", () -> invoices(scope, window), builder)
        ));
        Specification<BillingCharge> unbilled = DailyBriefingBillingSpecification.chargesForScope(scope)
                .and(DailyBriefingBillingSpecification.statusNotIn(INACTIVE_CHARGES))
                .and(DailyBriefingBillingSpecification.unbilledCompletedOpen(window.previousDay(), COMPLETED));
        builder.unbilled(read("billing.unbilledCount", () -> billingChargeRepository.findAll(unbilled).stream()
                .map(charge -> charge.getEncounter() == null ? null : charge.getEncounter().getId())
                .filter(Objects::nonNull)
                .distinct()
                .count(), builder), read("billing.unbilled", () -> moneyRows(
                billingChargeRepository.findAll(unbilled),
                charge -> charge.getCurrency() == null ? null : charge.getCurrency().name(),
                BillingCharge::getNetAmount,
                ignored -> null
        ), builder));
    }

    private void loadInsurance(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        builder.authorizations(read("insurance.authorizations", () -> authRows(scope), builder));
        Specification<BillingChargeResponsibility> holds = DailyBriefingBillingSpecification.responsibilitiesForScope(scope)
                .and(DailyBriefingBillingSpecification.pendingAuthorizationHold(INACTIVE_CHARGES));
        builder.servicesOnHold(read("insurance.holds.count", () -> billingChargeResponsibilityRepository.findAll(
                        holds.and(DailyBriefingBillingSpecification.withPatientServiceProduct())
                ).stream()
                .map(row -> row.getPatientServiceProduct() == null ? null : row.getPatientServiceProduct().getId())
                .filter(Objects::nonNull)
                .distinct()
                .count(), builder), read("insurance.holds", () -> moneyRows(
                billingChargeResponsibilityRepository.findAll(holds),
                responsibility -> responsibility.getCurrency() == null ? null : responsibility.getCurrency().name(),
                BillingChargeResponsibility::getResponsibilityAmount,
                ignored -> null
        ), builder));
        builder.claims(read("insurance.claims", () -> claimRows(scope, null, null), builder));
        builder.claimsSubmittedYesterday(read("insurance.claimsSubmitted", () -> claimRows(scope, window.previousStart(), window.previousEnd()), builder));
        builder.patientInsurance(read("insurance.patientInsurance", () -> patientInsuranceSnapshot(scope, window), builder));
    }

    private void loadOrders(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        Instant cutoff = window.refreshedAt().minusSeconds(window.turnaroundMinutes() * 60L);
        Specification<DiagnosticOrderTest> open = DailyBriefingClaimSpecification.testsForScope(scope)
                .and(DailyBriefingClaimSpecification.processingStatusNotIn(ORDER_DONE));
        Long overdue = read("orders.overdue", () -> diagnosticOrderTestRepository.count(open.and(DailyBriefingClaimSpecification.createdBefore(cutoff))), builder);
        Long within = read("orders.within", () -> diagnosticOrderTestRepository.count(open.and(DailyBriefingClaimSpecification.createdOnOrAfter(cutoff))), builder);
        Long missing = read("orders.missingAge", () -> diagnosticOrderTestRepository.count(open.and(DailyBriefingClaimSpecification.createdDateMissing())), builder);
        List<DailyBriefingFacts.TatSample> samples = read("orders.turnaround", () -> turnaround(scope, window), builder);
        builder.orders(new DailyBriefingFacts.OrderSnapshot(samples, false, 0, overdue, within, missing));
    }

    private DailyBriefingFacts.UrgentCareSnapshot loadUrgentCare(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        Boolean module = read("urgentCare.module", () -> hasUrgentCareModule(scope.facilityId()), builder);
        if (module == null || !module) {
            return new DailyBriefingFacts.UrgentCareSnapshot(module, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        Long admissions = read("urgentCare.admissions", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                        .and(DailyBriefingEncounterSpecification.hasBedAssignBetween(window.previousStart(), window.previousEnd()))
        ), builder);
        Long visits = read("urgentCare.visits", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterDate(window.previousDay()))
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
        ), builder);
        Long waitingList = read("urgentCare.waitingList", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterDate(window.previousDay()))
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                        .and(DailyBriefingEncounterSpecification.statusEquals(TreatmentStatus.WAITING_LIST))
        ), builder);
        Long inTriage = read("urgentCare.triage", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterDate(window.previousDay()))
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                        .and(DailyBriefingEncounterSpecification.statusIn(IN_TRIAGE))
        ), builder);
        Long inDepartment = read("urgentCare.inDepartment", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                        .and(DailyBriefingEncounterSpecification.statusIn(IN_DEPARTMENT))
        ), builder);
        Long discharged = read("urgentCare.discharged", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                        .and(DailyBriefingEncounterSpecification.dischargeBetween(
                                LocalDateTime.ofInstant(window.previousStart(), window.zone()),
                                LocalDateTime.ofInstant(window.previousEnd(), window.zone())))
        ), builder);
        Long occupied = read("urgentCare.occupied", () -> encounterAssignToBedRepository.findAll(
                DailyBriefingBedSpecification.occupiedAt(scope, EncounterType.EMERGENCY, window.occupancyAt())
        ).stream().map(EncounterAssignToBed::getBedId).filter(Objects::nonNull).distinct().count(), builder);
        StayStats dischargedStay = read("urgentCare.dischargedStay", () -> stays(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                        .and(DailyBriefingEncounterSpecification.dischargeBetween(
                                LocalDateTime.ofInstant(window.previousStart(), window.zone()),
                                LocalDateTime.ofInstant(window.previousEnd(), window.zone()))),
                window,
                true
        ), builder);
        StayStats currentStay = read("urgentCare.currentStay", () -> stays(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
                        .and(DailyBriefingEncounterSpecification.statusIn(IN_DEPARTMENT)),
                window,
                false
        ), builder);
        return new DailyBriefingFacts.UrgentCareSnapshot(true, admissions, visits, waitingList, inTriage, inDepartment, discharged, occupied,
                dischargedStay == null ? null : dischargedStay.average(), dischargedStay == null ? null : dischargedStay.sample(), dischargedStay == null ? null : dischargedStay.excluded(),
                currentStay == null ? null : currentStay.average(), currentStay == null ? null : currentStay.sample(), currentStay == null ? null : currentStay.excluded());
    }

    private DailyBriefingFacts.ComparisonCounts loadComparison(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        Long completed = read("comparison.encounters", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterDate(window.comparisonDay()))
                        .and(DailyBriefingEncounterSpecification.statusIn(COMPLETED))
        ), builder);
        Long patients = read("comparison.patients", () -> patientEncounterRepository.findAll(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterDate(window.comparisonDay()))
                        .and(DailyBriefingEncounterSpecification.statusIn(COMPLETED))
                        .and(DailyBriefingEncounterSpecification.withPatient())
        ).stream().map(encounter -> encounter.getPatient() == null ? null : encounter.getPatient().getId()).filter(Objects::nonNull).distinct().count(), builder);
        Long cancelled = read("comparison.cancelled", () -> patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterDate(window.comparisonDay()))
                        .and(DailyBriefingEncounterSpecification.statusEquals(TreatmentStatus.CANCELLED))
        ), builder);
        List<DailyBriefingFacts.StatusCount> appointments = read("comparison.appointments", () -> statusCounts(scope, window.comparisonStart(), window.comparisonEnd()), builder);
        Long noShows = appointments == null ? null : DailyBriefingCalculator.sum(appointments, AppointmentStatus.NO_SHOW.name()::equals);
        Long eligible = appointments == null ? null : DailyBriefingCalculator.sum(appointments, status -> {
            try {
                return DailyBriefingCalculator.countsAsEligibleAppointment(AppointmentStatus.valueOf(status));
            } catch (IllegalArgumentException ex) {
                return false;
            }
        });
        List<DailyBriefingFacts.MoneyRow> gross = read("comparison.gross", () -> moneyRows(
                billingChargeRepository.findAll(
                        DailyBriefingBillingSpecification.chargesForScope(scope)
                                .and(DailyBriefingBillingSpecification.chargeDateBetween(window.comparisonStart(), window.comparisonEnd()))
                                .and(DailyBriefingBillingSpecification.statusNotIn(INACTIVE_CHARGES))
                ),
                charge -> charge.getCurrency() == null ? null : charge.getCurrency().name(),
                BillingCharge::getGrossAmount,
                ignored -> null
        ), builder);
        return new DailyBriefingFacts.ComparisonCounts(completed, patients, cancelled, noShows, eligible, gross);
    }

    private boolean hasUrgentCareModule(Long facilityId) {
        return patientEncounterRepository.count(
                DailyBriefingEncounterSpecification.facilityId(facilityId)
                        .and(DailyBriefingEncounterSpecification.encounterType(EncounterType.EMERGENCY))
        ) > 0;
    }

    private List<DailyBriefingFacts.EncounterRow> encounterRows(Specification<PatientEncounter> specification) {
        List<PatientEncounter> encounters = patientEncounterRepository.findAll(
                specification.and(DailyBriefingEncounterSpecification.withPatientAndAppointment()),
                Sort.by("id")
        );
        Map<Long, String> departments = departmentNames(encounters.stream().map(PatientEncounter::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<DailyBriefingFacts.EncounterRow> rows = new ArrayList<>();
        for (PatientEncounter encounter : encounters) {
            Appointment appointment = encounter.getAppointment();
            String chief = encounter.getChiefComplaint();
            rows.add(new DailyBriefingFacts.EncounterRow(
                    encounter.getId(),
                    encounter.getPatient() == null ? null : encounter.getPatient().getId(),
                    encounter.getDepartmentId(),
                    departments.get(encounter.getDepartmentId()),
                    encounter.getPractitionerId(),
                    encounter.getEncounterReason(),
                    encounter.getStatus(),
                    encounter.getBillingStatus(),
                    encounter.getEncounterType(),
                    encounter.getEncounterDate(),
                    encounter.getEncounterTime(),
                    encounter.getStartedDate(),
                    encounter.getDischargeAt(),
                    chief != null && !chief.isBlank(),
                    appointment == null ? null : appointment.getCheckedInAt()
            ));
        }
        return rows;
    }

    private List<DailyBriefingFacts.StatusCount> statusCounts(BriefingScope scope, Instant start, Instant end) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Appointment appointment : appointmentRepository.findAll(
                DailyBriefingAppointmentSpecification.forScope(scope)
                        .and(DailyBriefingAppointmentSpecification.patientPresent())
                        .and(DailyBriefingAppointmentSpecification.startBetween(start, end))
        )) {
            String status = appointment.getStatus() == null ? "UNKNOWN" : appointment.getStatus().name();
            counts.merge(status, 1L, Long::sum);
        }
        return counts.entrySet().stream().map(entry -> new DailyBriefingFacts.StatusCount(entry.getKey(), entry.getValue())).toList();
    }

    private List<DailyBriefingFacts.DimensionCount> departmentCounts(BriefingScope scope, Instant start, Instant end) {
        List<Appointment> appointments = appointmentRepository.findAll(
                DailyBriefingAppointmentSpecification.forScope(scope)
                        .and(DailyBriefingAppointmentSpecification.patientPresent())
                        .and(DailyBriefingAppointmentSpecification.startBetween(start, end))
        );
        Map<Long, String> departments = departmentNames(appointments.stream().map(Appointment::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<String, DailyBriefingFacts.DimensionCount> rows = new LinkedHashMap<>();
        for (Appointment appointment : appointments) {
            String key = appointment.getDepartmentId() == null ? "UNKNOWN" : appointment.getDepartmentId().toString();
            String status = appointment.getStatus() == null ? "UNKNOWN" : appointment.getStatus().name();
            String compound = key + "|" + status;
            DailyBriefingFacts.DimensionCount current = rows.get(compound);
            if (current == null) {
                rows.put(compound, new DailyBriefingFacts.DimensionCount(key, departments.get(appointment.getDepartmentId()), status, 1L));
            } else {
                rows.put(compound, new DailyBriefingFacts.DimensionCount(current.key(), current.label(), current.status(), current.count() + 1));
            }
        }
        return new ArrayList<>(rows.values());
    }

    private List<DailyBriefingFacts.StatusCount> serviceCounts(BriefingScope scope, Instant start, Instant end) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Appointment appointment : appointmentRepository.findAll(
                DailyBriefingAppointmentSpecification.forScope(scope)
                        .and(DailyBriefingAppointmentSpecification.patientPresent())
                        .and(DailyBriefingAppointmentSpecification.startBetween(start, end))
        )) {
            String service = appointment.getService() == null ? "UNKNOWN" : appointment.getService().name();
            counts.merge(service, 1L, Long::sum);
        }
        return counts.entrySet().stream().map(entry -> new DailyBriefingFacts.StatusCount(entry.getKey(), entry.getValue())).toList();
    }

    private List<DailyBriefingFacts.AppointmentRow> appointmentRows(Specification<Appointment> specification) {
        List<DailyBriefingFacts.AppointmentRow> rows = new ArrayList<>();
        for (Appointment appointment : appointmentRepository.findAll(specification, Sort.by("id"))) {
            Long practitioner = appointment.getDefaultPractitionerId();
            if (practitioner == null && appointment.getResourceType() == TemplateType.PRACTITIONER) {
                practitioner = appointment.getResourceId();
            }
            rows.add(new DailyBriefingFacts.AppointmentRow(
                    appointment.getId(),
                    appointment.getStatus(),
                    appointment.getStartDatetime(),
                    appointment.getEndDatetime(),
                    practitioner,
                    Boolean.TRUE.equals(appointment.getRequirePractitioner())
            ));
        }
        return rows;
    }

    private List<DailyBriefingFacts.ChargeRow> chargeRows(BriefingScope scope, Instant start, Instant end) {
        Map<String, ChargeAccumulator> totals = new LinkedHashMap<>();
        for (BillingCharge charge : billingChargeRepository.findAll(
                DailyBriefingBillingSpecification.chargesForScope(scope)
                        .and(DailyBriefingBillingSpecification.chargeDateBetween(start, end))
                        .and(DailyBriefingBillingSpecification.statusNotIn(INACTIVE_CHARGES))
        )) {
            String currency = charge.getCurrency() == null ? "UNSPECIFIED" : charge.getCurrency().name();
            ChargeAccumulator accumulator = totals.computeIfAbsent(currency, ignored -> new ChargeAccumulator());
            accumulator.gross = accumulator.gross.add(zero(charge.getGrossAmount()));
            accumulator.discount = accumulator.discount.add(zero(charge.getDiscountAmount()));
            accumulator.exemption = accumulator.exemption.add(zero(charge.getExemptionAmount()));
            accumulator.net = accumulator.net.add(zero(charge.getNetAmount()));
            accumulator.count++;
        }
        List<DailyBriefingFacts.ChargeRow> rows = new ArrayList<>();
        totals.forEach((currency, accumulator) -> rows.add(new DailyBriefingFacts.ChargeRow(
                currency, accumulator.gross, accumulator.discount, accumulator.exemption, accumulator.net, accumulator.count
        )));
        return rows;
    }

    private List<DailyBriefingFacts.MoneyRow> invoices(BriefingScope scope, BriefingWindow window) {
        return moneyRows(
                financialDocumentRepository.findAll(
                        DailyBriefingBillingSpecification.issuedInvoices(scope, window.previousStart(), window.previousEnd())
                ),
                document -> document.getCurrency() == null ? null : document.getCurrency().name(),
                FinancialDocument::getTotalAmount,
                ignored -> null
        );
    }

    private List<DailyBriefingFacts.AuthRow> authRows(BriefingScope scope) {
        List<PreAuthorizationRequest> requests = preAuthorizationRequestRepository.findAll(
                DailyBriefingClaimSpecification.authorizationsForScope(scope)
        );
        Map<Long, PatientInsurance> insurances = patientInsurancesById(
                requests.stream().map(PreAuthorizationRequest::getPatientInsuranceId).filter(Objects::nonNull).collect(Collectors.toSet())
        );
        Map<String, AuthAccumulator> totals = new LinkedHashMap<>();
        for (PreAuthorizationRequest request : requests) {
            PatientInsurance insurance = insurances.get(request.getPatientInsuranceId());
            Long payorId = insurance != null && insurance.getPayorId() != null ? insurance.getPayorId() : request.getPayorId();
            String key = request.getStatus() + "|" + request.getOutcome() + "|" + payorId + "|" + request.getDateOrdered();
            AuthAccumulator accumulator = totals.computeIfAbsent(
                    key,
                    ignored -> new AuthAccumulator(request.getStatus(), request.getOutcome(), payorId, request.getDateOrdered())
            );
            accumulator.count++;
            accumulator.requestedNet = accumulator.requestedNet.add(zero(request.getTotalNet()));
        }
        return totals.values().stream()
                .map(row -> new DailyBriefingFacts.AuthRow(row.status, row.outcome, row.payorId, row.orderedOn, row.count, row.requestedNet))
                .toList();
    }

    private List<DailyBriefingFacts.ClaimRow> claimRows(BriefingScope scope, Instant start, Instant end) {
        Specification<ClaimRequest> specification = DailyBriefingClaimSpecification.forScope(scope);
        if (start != null) {
            specification = specification.and(DailyBriefingClaimSpecification.submittedBetween(start, end, SUBMITTED));
        }
        List<ClaimRequest> claims = claimRequestRepository.findAll(specification);
        Map<Long, PatientInsurance> insurances = patientInsurancesById(
                claims.stream().map(ClaimRequest::getPatientInsuranceId).filter(Objects::nonNull).collect(Collectors.toSet())
        );
        Map<String, ClaimAccumulator> totals = new LinkedHashMap<>();
        for (ClaimRequest claim : claims) {
            PatientInsurance insurance = insurances.get(claim.getPatientInsuranceId());
            Long payorId = insurance == null ? null : insurance.getPayorId();
            String payerName = insurance == null ? null : insurance.getPayerName();
            String key = claim.getStatus() + "|" + claim.getOutcome() + "|" + payorId + "|" + payerName;
            ClaimAccumulator accumulator = totals.computeIfAbsent(key, ignored -> new ClaimAccumulator(
                    claim.getStatus() == null ? null : claim.getStatus().name(),
                    claim.getOutcome(),
                    payorId,
                    payerName
            ));
            accumulator.count++;
            accumulator.claimedNet = accumulator.claimedNet.add(zero(claim.getTotalNet()));
        }
        return totals.values().stream()
                .map(row -> new DailyBriefingFacts.ClaimRow(row.status, row.outcome, row.payorId, row.payerName, row.count, row.claimedNet))
                .toList();
    }

    private DailyBriefingFacts.PatientInsuranceSnapshot patientInsuranceSnapshot(BriefingScope scope, BriefingWindow window) {
        List<PatientEncounter> encounters = patientEncounterRepository.findAll(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(DailyBriefingEncounterSpecification.encounterDate(window.previousDay()))
                        .and(DailyBriefingEncounterSpecification.withPatient())
        );
        Set<Long> patientIds = encounters.stream()
                .map(encounter -> encounter.getPatient() == null ? null : encounter.getPatient().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (patientIds.isEmpty()) {
            return new DailyBriefingFacts.PatientInsuranceSnapshot(0L, 0L, 0L, 0L);
        }
        List<PatientInsurance> insurances = patientInsuranceRepository.findByPatient_IdIn(patientIds);
        Set<Long> insuredPatients = insurances.stream()
                .map(insurance -> insurance.getPatient() == null ? null : insurance.getPatient().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> insuranceIds = insurances.stream().map(PatientInsurance::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> withRules = insuranceIds.isEmpty()
                ? Set.of()
                : patientInsuranceBenefitRuleRepository.findByPatientInsuranceIdIn(insuranceIds).stream()
                        .map(PatientInsuranceBenefitRule::getPatientInsuranceId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        long missing = insuranceIds.stream().filter(id -> !withRules.contains(id)).count();
        return new DailyBriefingFacts.PatientInsuranceSnapshot(
                (long) insuredPatients.size(),
                (long) insuranceIds.size(),
                (long) withRules.size(),
                missing
        );
    }

    private Map<Long, PatientInsurance> patientInsurancesById(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return patientInsuranceRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PatientInsurance::getId, Function.identity(), (left, right) -> left));
    }

    private List<DailyBriefingFacts.TatSample> turnaround(BriefingScope scope, BriefingWindow window) {
        List<DiagnosticOrderTest> tests = diagnosticOrderTestRepository.findAll(
                DailyBriefingClaimSpecification.testsForScope(scope)
                        .and(DailyBriefingClaimSpecification.turnaroundCompletedBetween(window.previousStart(), window.previousEnd())),
                PageRequest.of(0, MAX_ROWS, Sort.by("id"))
        ).getContent();
        List<DailyBriefingFacts.TatSample> samples = new ArrayList<>();
        for (DiagnosticOrderTest test : tests) {
            Instant end = test.getApprovedDate() != null ? test.getApprovedDate() : test.getReadyDate();
            Instant start = test.getPatientArrivedDate() != null ? test.getPatientArrivedDate() : test.getSubmitDate();
            if (start == null || end == null || end.isBefore(start)) {
                continue;
            }
            samples.add(new DailyBriefingFacts.TatSample(
                    test.getOrderType() == null ? "UNKNOWN" : test.getOrderType().name(),
                    Duration.between(start, end).toMinutes()
            ));
        }
        return samples;
    }

    private StayStats stays(Specification<PatientEncounter> specification, BriefingWindow window, boolean discharged) {
        List<BigDecimal> hours = new ArrayList<>();
        int excluded = 0;
        for (PatientEncounter encounter : patientEncounterRepository.findAll(specification)) {
            Instant start = encounter.getStartedDate();
            if (start == null && encounter.getEncounterDate() != null) {
                LocalTime time = encounter.getEncounterTime() == null ? LocalTime.MIN : encounter.getEncounterTime();
                start = encounter.getEncounterDate().atTime(time).atZone(window.zone()).toInstant();
            }
            Instant end = discharged && encounter.getDischargeAt() != null
                    ? encounter.getDischargeAt().atZone(window.zone()).toInstant()
                    : window.refreshedAt();
            BigDecimal stay = DailyBriefingCalculator.hoursBetween(start, end);
            if (stay == null) {
                excluded++;
            } else {
                hours.add(stay);
            }
        }
        return new StayStats(DailyBriefingCalculator.average(hours), hours.size(), excluded);
    }

    private List<DailyBriefingCase> encounterCases(BriefingScope scope, Specification<PatientEncounter> extra, int limit) {
        List<PatientEncounter> encounters = patientEncounterRepository.findAll(
                DailyBriefingEncounterSpecification.forScope(scope)
                        .and(extra)
                        .and(DailyBriefingEncounterSpecification.withPatient()),
                PageRequest.of(0, limit, Sort.by("id"))
        ).getContent();
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (PatientEncounter encounter : encounters) {
            cases.add(new DailyBriefingCase(
                    encounter.getId(),
                    "ENCOUNTER",
                    encounter.getPatient() == null ? null : encounter.getPatient().getId(),
                    encounter.getDepartmentId(),
                    encounter.getPractitionerId(),
                    encounter.getStatus() == null ? null : encounter.getStatus().name(),
                    encounter.getStartedDate(),
                    null,
                    null,
                    null
            ));
        }
        return cases;
    }

    private List<DailyBriefingCase> appointmentCases(BriefingScope scope, Specification<Appointment> extra, Instant start, Instant end, int limit) {
        List<Appointment> appointments = appointmentRepository.findAll(
                DailyBriefingAppointmentSpecification.forScope(scope)
                        .and(extra)
                        .and(DailyBriefingAppointmentSpecification.startBetween(start, end))
                        .and(DailyBriefingAppointmentSpecification.withPatient()),
                PageRequest.of(0, limit, Sort.by("id"))
        ).getContent();
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (Appointment appointment : appointments) {
            cases.add(new DailyBriefingCase(
                    appointment.getId(),
                    "APPOINTMENT",
                    appointment.getPatient() == null ? null : appointment.getPatient().getId(),
                    appointment.getDepartmentId(),
                    appointment.getDefaultPractitionerId(),
                    appointment.getStatus() == null ? null : appointment.getStatus().name(),
                    appointment.getStartDatetime(),
                    null,
                    null,
                    null
            ));
        }
        return cases;
    }

    private List<DailyBriefingCase> conflictCases(BriefingScope scope, BriefingWindow window, int limit) {
        List<DailyBriefingFacts.AppointmentRow> booked = appointmentRows(
                DailyBriefingAppointmentSpecification.forScope(scope)
                        .and(DailyBriefingAppointmentSpecification.patientPresent())
                        .and(DailyBriefingAppointmentSpecification.startBetween(window.currentStart(), window.currentEnd()))
        );
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (DailyBriefingCalculator.Conflict conflict : DailyBriefingCalculator.conflicts(booked)) {
            cases.add(new DailyBriefingCase(conflict.firstAppointmentId(), "APPOINTMENT", null, null, conflict.practitionerId(), "OVERLAP", null, null, null, null));
            cases.add(new DailyBriefingCase(conflict.secondAppointmentId(), "APPOINTMENT", null, null, conflict.practitionerId(), "OVERLAP", null, null, null, null));
            if (cases.size() >= limit) {
                break;
            }
        }
        return cases;
    }

    private List<DailyBriefingCase> authorizationCases(BriefingScope scope, Drill drill, int limit) {
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (PreAuthorizationRequest request : preAuthorizationRequestRepository.findAll(
                DailyBriefingClaimSpecification.authorizationsForScope(scope),
                PageRequest.of(0, MAX_ROWS, Sort.by("id"))
        ).getContent()) {
            boolean denial = DailyBriefingCalculator.isAuthorizationDenial(request.getStatus(), null);
            boolean pending = DailyBriefingCalculator.isClassifiedPendingAuthorization(request.getStatus(), null)
                    || DailyBriefingCalculator.isUnclassifiedOpenAuthorization(request.getStatus(), null);
            if (drill == Drill.AUTHORIZATION_DENIALS && !denial) {
                continue;
            }
            if (drill == Drill.PENDING_AUTHORIZATIONS && !pending) {
                continue;
            }
            PatientEncounter encounter = patientEncounterRepository.findById(request.getEncounterId()).orElse(null);
            cases.add(new DailyBriefingCase(
                    request.getId(),
                    "AUTHORIZATION",
                    request.getPatientId(),
                    encounter == null ? null : encounter.getDepartmentId(),
                    encounter == null ? null : encounter.getPractitionerId(),
                    request.getStatus(),
                    null,
                    zero(request.getTotalNet()),
                    null,
                    null
            ));
            if (cases.size() >= limit) {
                break;
            }
        }
        return cases;
    }

    private List<DailyBriefingCase> holdCases(BriefingScope scope, int limit) {
        List<BillingChargeResponsibility> rows = billingChargeResponsibilityRepository.findAll(
                DailyBriefingBillingSpecification.responsibilitiesForScope(scope)
                        .and(DailyBriefingBillingSpecification.pendingAuthorizationHold(INACTIVE_CHARGES))
                        .and(DailyBriefingBillingSpecification.withPatientServiceProduct()),
                PageRequest.of(0, limit, Sort.by("id"))
        ).getContent();
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (BillingChargeResponsibility responsibility : rows) {
            cases.add(new DailyBriefingCase(
                    responsibility.getId(),
                    "SERVICE",
                    responsibility.getPatient() == null ? null : responsibility.getPatient().getId(),
                    responsibility.getEncounter() == null ? null : responsibility.getEncounter().getDepartmentId(),
                    responsibility.getEncounter() == null ? null : responsibility.getEncounter().getPractitionerId(),
                    responsibility.getPreAuthorizationStatus() == null ? null : responsibility.getPreAuthorizationStatus().name(),
                    null,
                    zero(responsibility.getResponsibilityAmount()),
                    responsibility.getCurrency() == null ? null : responsibility.getCurrency().name(),
                    null
            ));
        }
        return cases;
    }

    private List<DailyBriefingCase> claimCases(BriefingScope scope, ClaimStatus status, int limit) {
        List<ClaimRequest> claims = claimRequestRepository.findAll(
                DailyBriefingClaimSpecification.forScope(scope).and(DailyBriefingClaimSpecification.statusEquals(status)),
                PageRequest.of(0, limit, Sort.by("id"))
        ).getContent();
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (ClaimRequest claim : claims) {
            PatientEncounter encounter = patientEncounterRepository.findById(claim.getEncounterId()).orElse(null);
            cases.add(new DailyBriefingCase(
                    claim.getId(),
                    "CLAIM",
                    claim.getPatientId(),
                    encounter == null ? null : encounter.getDepartmentId(),
                    encounter == null ? null : encounter.getPractitionerId(),
                    claim.getStatus() == null ? null : claim.getStatus().name(),
                    claim.getSubmittedAt(),
                    zero(claim.getTotalNet()),
                    null,
                    null
            ));
        }
        return cases;
    }

    private List<DailyBriefingCase> orderCases(BriefingScope scope, BriefingWindow window, int limit) {
        Instant cutoff = window.refreshedAt().minusSeconds(window.turnaroundMinutes() * 60L);
        List<DiagnosticOrderTest> tests = diagnosticOrderTestRepository.findAll(
                DailyBriefingClaimSpecification.testsForScope(scope)
                        .and(DailyBriefingClaimSpecification.processingStatusNotIn(ORDER_DONE))
                        .and(DailyBriefingClaimSpecification.createdBefore(cutoff)),
                PageRequest.of(0, limit, Sort.by("createdDate"))
        ).getContent();
        Map<Long, DiagnosticOrder> orders = diagnosticOrderRepository.findAllById(
                tests.stream().map(DiagnosticOrderTest::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(DiagnosticOrder::getId, Function.identity()));
        Map<Long, PatientEncounter> encounters = patientEncounterRepository.findAllById(
                orders.values().stream().map(DiagnosticOrder::getEncounterId).filter(Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(PatientEncounter::getId, Function.identity()));
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (DiagnosticOrderTest test : tests) {
            DiagnosticOrder order = orders.get(test.getOrderId());
            PatientEncounter encounter = order == null ? null : encounters.get(order.getEncounterId());
            Instant created = test.getCreatedDate();
            Long age = created == null ? null : Duration.between(created, window.refreshedAt()).toHours();
            cases.add(new DailyBriefingCase(
                    test.getId(),
                    "ORDER",
                    encounter == null || encounter.getPatient() == null ? null : encounter.getPatient().getId(),
                    test.getReceivedDepartmentId(),
                    encounter == null ? null : encounter.getPractitionerId(),
                    test.getProcessingStatus() == null ? null : test.getProcessingStatus().name(),
                    created,
                    null,
                    null,
                    age
            ));
        }
        return cases;
    }

    private Map<Long, String> departmentNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (left, right) -> left));
    }

    private <T> List<DailyBriefingFacts.MoneyRow> moneyRows(
            List<T> values,
            Function<T, String> currency,
            Function<T, BigDecimal> amount,
            Function<T, String> label
    ) {
        Map<String, MoneyAccumulator> totals = new LinkedHashMap<>();
        for (T value : values) {
            String code = currency.apply(value) == null ? "UNSPECIFIED" : currency.apply(value);
            String group = label.apply(value);
            String key = group == null ? code : code + "|" + group;
            MoneyAccumulator accumulator = totals.computeIfAbsent(key, ignored -> new MoneyAccumulator(code, group));
            accumulator.amount = accumulator.amount.add(zero(amount.apply(value)));
            accumulator.count++;
        }
        List<DailyBriefingFacts.MoneyRow> rows = new ArrayList<>();
        totals.values().forEach(row -> rows.add(new DailyBriefingFacts.MoneyRow(row.currency, row.amount, row.count, row.label)));
        return rows;
    }

    private <T> T read(String code, java.util.function.Supplier<T> supplier, DailyBriefingFacts.Builder builder) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            log.error("Daily briefing query failed: {}", code, ex);
            builder.failure(code);
            return null;
        }
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class ChargeAccumulator {
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal discount = BigDecimal.ZERO;
        private BigDecimal exemption = BigDecimal.ZERO;
        private BigDecimal net = BigDecimal.ZERO;
        private long count;
    }

    private static final class MoneyAccumulator {
        private final String currency;
        private final String label;
        private BigDecimal amount = BigDecimal.ZERO;
        private long count;

        private MoneyAccumulator(String currency, String label) {
            this.currency = currency;
            this.label = label;
        }
    }

    private static final class AuthAccumulator {
        private final String status;
        private final String outcome;
        private final Long payorId;
        private final LocalDate orderedOn;
        private long count;
        private BigDecimal requestedNet = BigDecimal.ZERO;

        private AuthAccumulator(String status, String outcome, Long payorId, LocalDate orderedOn) {
            this.status = status;
            this.outcome = outcome;
            this.payorId = payorId;
            this.orderedOn = orderedOn;
        }
    }

    private static final class ClaimAccumulator {
        private final String status;
        private final String outcome;
        private final Long payorId;
        private final String payerName;
        private long count;
        private BigDecimal claimedNet = BigDecimal.ZERO;

        private ClaimAccumulator(String status, String outcome, Long payorId, String payerName) {
            this.status = status;
            this.outcome = outcome;
            this.payorId = payorId;
            this.payerName = payerName;
        }
    }

    private record StayStats(BigDecimal average, int sample, int excluded) {
    }
}
