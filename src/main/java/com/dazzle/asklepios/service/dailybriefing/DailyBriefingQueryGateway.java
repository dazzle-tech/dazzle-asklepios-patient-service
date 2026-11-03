package com.dazzle.asklepios.service.dailybriefing;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundStatus;
import com.dazzle.asklepios.domain.enumeration.BedTransactionType;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingCasesResponse.DailyBriefingCase;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Drill;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class DailyBriefingQueryGateway {

    private static final Logger log = LoggerFactory.getLogger(DailyBriefingQueryGateway.class);
    static final int MAX_ROWS = 20000;

    private static final List<TreatmentStatus> COMPLETED = List.of(TreatmentStatus.COMPLETED, TreatmentStatus.CLOSED, TreatmentStatus.DISCHARGED);
    private static final List<TreatmentStatus> NOT_OPEN = List.of(TreatmentStatus.COMPLETED, TreatmentStatus.CLOSED, TreatmentStatus.DISCHARGED, TreatmentStatus.CANCELLED);
    private static final List<BillingChargeStatus> INACTIVE_CHARGES = List.of(BillingChargeStatus.CANCELLED, BillingChargeStatus.REVERSED);
    private static final List<FinancialDocumentStatus> INACTIVE_DOCUMENTS = List.of(FinancialDocumentStatus.DRAFT, FinancialDocumentStatus.CANCELLED);
    private static final List<BillingRefundStatus> REFUND_DONE = List.of(BillingRefundStatus.COMPLETED, BillingRefundStatus.PARTIALLY_COMPLETED);
    private static final List<AppointmentStatus> OFF_BOOK = List.of(AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED);
    private static final List<DiagnosticStatus> ORDER_DONE = List.of(DiagnosticStatus.RESULT_APPROVED, DiagnosticStatus.CANCELLED, DiagnosticStatus.REJECTED, DiagnosticStatus.COMPLETED);
    private static final List<ClaimStatus> SUBMITTED = List.of(ClaimStatus.SUBMITTED, ClaimStatus.ACCEPTED, ClaimStatus.REJECTED, ClaimStatus.FAILED);
    private static final List<TreatmentStatus> IN_DEPARTMENT = List.of(TreatmentStatus.ONGOING, TreatmentStatus.ASSIGNED_TO_BED);
    private static final List<TreatmentStatus> IN_TRIAGE = List.of(TreatmentStatus.WAITING_TRIAGE, TreatmentStatus.TRIAGE_STARTED);

    private final EntityManager entityManager;

    public DailyBriefingQueryGateway(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Set<Long> practitionerIds(Long facilityId, Long departmentId, LocalDate fromDay, LocalDate toDay, Instant from, Instant to) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(longs("select distinct e.practitionerId from PatientEncounter e where e.facilityId = :facilityId and e.encounterDate >= :fromDay and e.encounterDate <= :toDay and e.practitionerId is not null"
                + department("e", departmentId), facilityId, departmentId, query -> {
            query.setParameter("fromDay", fromDay);
            query.setParameter("toDay", toDay);
        }));
        ids.addAll(longs("select distinct a.defaultPractitionerId from Appointment a where a.facilityId = :facilityId and a.startDatetime >= :from and a.startDatetime < :to and a.defaultPractitionerId is not null"
                + department("a", departmentId), facilityId, departmentId, query -> {
            query.setParameter("from", from);
            query.setParameter("to", to);
        }));
        ids.addAll(longs("select distinct a.resourceId from Appointment a where a.facilityId = :facilityId and a.resourceType = :resourceType and a.startDatetime >= :from and a.startDatetime < :to"
                + department("a", departmentId), facilityId, departmentId, query -> {
            query.setParameter("resourceType", TemplateType.PRACTITIONER);
            query.setParameter("from", from);
            query.setParameter("to", to);
        }));
        ids.remove(null);
        return ids;
    }

    public DailyBriefingFacts load(BriefingScope scope, BriefingWindow window) {
        DailyBriefingFacts.Builder builder = DailyBriefingFacts.builder();
        if (scope.matchNone()) {
            Boolean module = read("urgentCare.module", () -> count(
                    "select count(e.id) from PatientEncounter e where e.facilityId = :facilityId and e.encounterType = :type",
                    new BriefingScope(scope.facilityId(), null, null, null, false),
                    query -> query.setParameter("type", EncounterType.EMERGENCY)
            ) > 0, builder);
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
            case COMPLETED_ENCOUNTERS -> encounterCases(scope, "e.encounterDate = :day and e.status in :statuses", window.previousDay(), COMPLETED, fetch);
            case CANCELLED_ENCOUNTERS -> encounterCases(scope, "e.encounterDate = :day and e.status = :cancelled", window.previousDay(), null, fetch);
            case INCOMPLETE_ENCOUNTERS -> encounterCases(scope, "e.encounterDate = :day and e.status not in :statuses", window.previousDay(), NOT_OPEN, fetch);
            case MISSING_CHIEF_COMPLAINT -> encounterCases(scope, "e.encounterDate = :day and e.status in :statuses and (e.chiefComplaint is null or e.chiefComplaint = '')", window.previousDay(), COMPLETED, fetch);
            case UNBILLED_ENCOUNTERS -> encounterCases(scope, "e.encounterDate = :day and e.status in :statuses and e.billingStatus = :billing", window.previousDay(), COMPLETED, fetch);
            case OPEN_ENCOUNTERS -> encounterCases(scope, "e.status not in :statuses and ((e.startedDate is not null and e.startedDate < :cutoff) or (e.startedDate is null and e.encounterDate <= :oldest))", null, NOT_OPEN, fetch, window);
            case URGENT_CARE_ADMISSIONS -> admissionCases(scope, window, fetch);
            case URGENT_CARE_VISITS -> encounterCases(scope, "e.encounterDate = :day and e.encounterType = :emergency", window.previousDay(), null, fetch);
            case URGENT_CARE_WAITING_LIST -> encounterCases(scope, "e.encounterDate = :day and e.encounterType = :emergency and e.status = :waitingList", window.previousDay(), null, fetch);
            case URGENT_CARE_TRIAGE -> encounterCases(scope, "e.encounterDate = :day and e.encounterType = :emergency and e.status in :statuses", window.previousDay(), IN_TRIAGE, fetch);
            case URGENT_CARE_IN_DEPARTMENT -> encounterCases(scope, "e.encounterType = :emergency and e.status in :statuses", null, IN_DEPARTMENT, fetch);
            case URGENT_CARE_DISCHARGES -> encounterCases(scope, "e.encounterType = :emergency and e.dischargeAt >= :rangeStart and e.dischargeAt < :rangeEnd", null, null, fetch, window);
            case TODAY_APPOINTMENTS -> appointmentCases(scope, "a.patient is not null", window.currentStart(), window.currentEnd(), fetch);
            case PREVIOUS_CANCELLATIONS -> appointmentCases(scope, "a.patient is not null and a.status = :cancelledAppointment", window.previousStart(), window.previousEnd(), fetch);
            case PREVIOUS_NO_SHOWS -> appointmentCases(scope, "a.patient is not null and a.status = :noShow", window.previousStart(), window.previousEnd(), fetch);
            case BOOKED_WITHOUT_PRACTITIONER -> appointmentCases(scope, "a.patient is not null and a.requirePractitioner = true and a.defaultPractitionerId is null and (a.resourceType <> :practitionerType or a.resourceId is null) and a.status not in :offBook", window.currentStart(), window.currentEnd(), fetch);
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
        Long total = read("encounters.previous", () -> count("select count(e.id) from PatientEncounter e where e.encounterDate = :day and " + encounterWhere(scope), scope, query -> query.setParameter("day", window.previousDay())), builder);
        if (total == null) {
            builder.previousEncounters(null);
        } else if (total > MAX_ROWS) {
            builder.previousEncountersCapped(true, total);
        } else {
            builder.previousEncounters(read("encounters.previous.rows", () -> encounterRows(scope, "e.encounterDate = :day", query -> query.setParameter("day", window.previousDay())), builder));
        }
        Long openTotal = read("encounters.open", () -> count("select count(e.id) from PatientEncounter e where e.status not in :done and " + encounterWhere(scope), scope, query -> query.setParameter("done", NOT_OPEN)), builder);
        if (openTotal == null) {
            builder.openEncounters(null);
        } else if (openTotal > MAX_ROWS) {
            builder.openEncountersCapped(true, openTotal);
        } else {
            builder.openEncounters(read("encounters.open.rows", () -> encounterRows(scope, "e.status not in :done", query -> query.setParameter("done", NOT_OPEN)), builder));
        }
    }

    private void loadAppointments(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        builder.currentAppointmentStatuses(read("appointments.current", () -> statusCounts(scope, window.currentStart(), window.currentEnd()), builder));
        builder.previousAppointmentStatuses(read("appointments.previous", () -> statusCounts(scope, window.previousStart(), window.previousEnd()), builder));
        builder.currentAppointmentsByDepartment(read("appointments.department", () -> departmentCounts(scope, window.currentStart(), window.currentEnd()), builder));
        builder.currentAppointmentsByService(read("appointments.service", () -> serviceCounts(scope, window.currentStart(), window.currentEnd()), builder));
        builder.currentOpenSlots(read("appointments.slots", () -> count("select count(a.id) from Appointment a where a.patient is null and a.status = :newStatus and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope), scope, query -> {
            query.setParameter("newStatus", AppointmentStatus.NEW);
            query.setParameter("start", window.currentStart());
            query.setParameter("end", window.currentEnd());
        }), builder));
        builder.bookedWithoutPractitioner(read("appointments.missingPractitioner", () -> count("select count(a.id) from Appointment a where a.patient is not null and a.requirePractitioner = true and a.defaultPractitionerId is null and (a.resourceType <> :practitionerType or a.resourceId is null) and a.status not in :offBook and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope), scope, query -> {
            query.setParameter("practitionerType", TemplateType.PRACTITIONER);
            query.setParameter("offBook", OFF_BOOK);
            query.setParameter("start", window.currentStart());
            query.setParameter("end", window.currentEnd());
        }), builder));
        Long booked = read("appointments.bookedCount", () -> count("select count(a.id) from Appointment a where a.patient is not null and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope), scope, query -> {
            query.setParameter("start", window.currentStart());
            query.setParameter("end", window.currentEnd());
        }), builder);
        if (booked == null) {
            builder.currentBooked(null);
        } else if (booked > MAX_ROWS) {
            builder.currentBookedCapped(true);
        } else {
            builder.currentBooked(read("appointments.booked", () -> appointmentRows(scope, window.currentStart(), window.currentEnd()), builder));
        }
    }

    private void loadBilling(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        builder.billing(new DailyBriefingFacts.BillingSnapshot(
                read("billing.charges", () -> chargeRows(scope, window), builder),
                read("billing.cancellations", () -> money("select c.currency, sum(c.netAmount), count(c.id) from BillingCharge c join c.encounter e where c.cancelledDate >= :start and c.cancelledDate < :end and c.status in :inactive and " + encounterWhere(scope) + " group by c.currency", scope, window, query -> query.setParameter("inactive", INACTIVE_CHARGES)), builder),
                read("billing.creditNotes", () -> documents(scope, window, FinancialDocumentType.CREDIT_NOTE), builder),
                read("billing.debitNotes", () -> documents(scope, window, FinancialDocumentType.DEBIT_NOTE), builder),
                read("billing.reversedUntimed", () -> count("select count(c.id) from BillingCharge c join c.encounter e where c.status = :reversed and c.cancelledDate is null and c.chargeDate >= :start and c.chargeDate < :end and " + encounterWhere(scope), scope, query -> {
                    query.setParameter("reversed", BillingChargeStatus.REVERSED);
                    query.setParameter("start", window.previousStart());
                    query.setParameter("end", window.previousEnd());
                }), builder),
                read("billing.payments", () -> money("select bp.currency, bp.payerType, sum(bp.amount), count(bp.id) from BillingPayment bp join bp.encounter e where bp.status = :completed and bp.paymentDate >= :start and bp.paymentDate < :end and " + encounterWhere(scope) + " group by bp.currency, bp.payerType", scope, window, query -> query.setParameter("completed", BillingPaymentStatus.COMPLETED)), builder),
                read("billing.refunds", () -> money("select p.currency, p.payerType, sum(r.refundedAmount), count(r.id) from BillingRefund r join r.originalPayment p join p.encounter e where r.status in :done and r.completedDate >= :start and r.completedDate < :end and " + encounterWhere(scope) + " group by p.currency, p.payerType", scope, window, query -> query.setParameter("done", REFUND_DONE)), builder),
                read("billing.unclassifiedRefunds", () -> count("select count(r.id) from BillingRefund r join r.encounter e where r.originalPayment is null and r.status in :done and r.completedDate >= :start and r.completedDate < :end and " + encounterWhere(scope), scope, query -> {
                    query.setParameter("done", REFUND_DONE);
                    query.setParameter("start", window.previousStart());
                    query.setParameter("end", window.previousEnd());
                }), builder),
                read("billing.responsibility", () -> money("select r.currency, r.responsiblePartyType, sum(r.responsibilityAmount), count(r.id) from BillingChargeResponsibility r join r.charge c join r.encounter e where r.supersededResponsibility is null and c.status not in :inactive and c.chargeDate >= :start and c.chargeDate < :end and " + encounterWhere(scope) + " group by r.currency, r.responsiblePartyType", scope, window, query -> query.setParameter("inactive", INACTIVE_CHARGES)), builder),
                read("billing.invoices", () -> documents(scope, window, FinancialDocumentType.INVOICE), builder)
        ));
        builder.unbilled(read("billing.unbilledCount", () -> count("select count(distinct e.id) from BillingCharge c join c.encounter e where e.encounterDate = :day and e.status in :completed and e.billingStatus = :open and c.status not in :inactive and " + encounterWhere(scope), scope, query -> {
            query.setParameter("day", window.previousDay());
            query.setParameter("completed", COMPLETED);
            query.setParameter("open", EncounterBillingStatus.OPEN);
            query.setParameter("inactive", INACTIVE_CHARGES);
        }), builder), read("billing.unbilled", () -> money("select c.currency, sum(c.netAmount), count(c.id) from BillingCharge c join c.encounter e where e.encounterDate = :day and e.status in :completed and e.billingStatus = :open and c.status not in :inactive and " + encounterWhere(scope) + " group by c.currency", scope, window, query -> {
            query.setParameter("day", window.previousDay());
            query.setParameter("completed", COMPLETED);
            query.setParameter("open", EncounterBillingStatus.OPEN);
            query.setParameter("inactive", INACTIVE_CHARGES);
        }), builder));
    }

    private void loadInsurance(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        builder.authorizations(read("insurance.authorizations", () -> authRows(scope), builder));
        builder.servicesOnHold(read("insurance.holds.count", () -> count("select count(distinct r.patientServiceProduct.id) from BillingChargeResponsibility r join r.charge c join r.encounter e where r.preAuthorizationRequired = true and r.preAuthorizationStatus = :pending and r.supersededResponsibility is null and c.status not in :inactive and " + encounterWhere(scope), scope, query -> {
            query.setParameter("pending", PreAuthorizationStatus.PENDING_APPROVAL);
            query.setParameter("inactive", INACTIVE_CHARGES);
        }), builder), read("insurance.holds", () -> money("select r.currency, sum(r.responsibilityAmount), count(r.id) from BillingChargeResponsibility r join r.charge c join r.encounter e where r.preAuthorizationRequired = true and r.preAuthorizationStatus = :pending and r.supersededResponsibility is null and c.status not in :inactive and " + encounterWhere(scope) + " group by r.currency", scope, null, query -> {
            query.setParameter("pending", PreAuthorizationStatus.PENDING_APPROVAL);
            query.setParameter("inactive", INACTIVE_CHARGES);
        }), builder));
        builder.claims(read("insurance.claims", () -> claimRows(scope, null, null), builder));
        builder.claimsSubmittedYesterday(read("insurance.claimsSubmitted", () -> claimRows(scope, window.previousStart(), window.previousEnd()), builder));
    }

    private void loadOrders(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        Long overdue = read("orders.overdue", () -> count("select count(t.id) from DiagnosticOrderTest t, DiagnosticOrder o, PatientEncounter e where t.orderId = o.id and o.encounterId = e.id and t.processingStatus not in :done and t.createdDate is not null and t.createdDate < :cutoff and " + encounterWhere(scope), scope, query -> {
            query.setParameter("done", ORDER_DONE);
            query.setParameter("cutoff", window.refreshedAt().minusSeconds(window.turnaroundMinutes() * 60L));
        }), builder);
        Long within = read("orders.within", () -> count("select count(t.id) from DiagnosticOrderTest t, DiagnosticOrder o, PatientEncounter e where t.orderId = o.id and o.encounterId = e.id and t.processingStatus not in :done and t.createdDate is not null and t.createdDate >= :cutoff and " + encounterWhere(scope), scope, query -> {
            query.setParameter("done", ORDER_DONE);
            query.setParameter("cutoff", window.refreshedAt().minusSeconds(window.turnaroundMinutes() * 60L));
        }), builder);
        Long missing = read("orders.missingAge", () -> count("select count(t.id) from DiagnosticOrderTest t, DiagnosticOrder o, PatientEncounter e where t.orderId = o.id and o.encounterId = e.id and t.processingStatus not in :done and t.createdDate is null and " + encounterWhere(scope), scope, query -> query.setParameter("done", ORDER_DONE)), builder);
        List<DailyBriefingFacts.TatSample> samples = read("orders.turnaround", () -> turnaround(scope, window), builder);
        builder.orders(new DailyBriefingFacts.OrderSnapshot(samples, false, 0, overdue, within, missing));
    }

    private DailyBriefingFacts.UrgentCareSnapshot loadUrgentCare(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        Boolean module = read("urgentCare.module", () -> count("select count(e.id) from PatientEncounter e where e.facilityId = :facilityId and e.encounterType = :type", new BriefingScope(scope.facilityId(), null, null, null, false), query -> query.setParameter("type", EncounterType.EMERGENCY)) > 0, builder);
        if (module == null || !module) {
            return new DailyBriefingFacts.UrgentCareSnapshot(module, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        Long admissions = read("urgentCare.admissions", () -> count("select count(e.id) from PatientEncounter e where e.encounterType = :type and exists (select 1 from BedTransaction b where b.encounter = e and b.transactionType = :assign and b.transactionDate >= :start and b.transactionDate < :end) and " + encounterWhere(scope), scope, query -> {
            query.setParameter("type", EncounterType.EMERGENCY);
            query.setParameter("assign", BedTransactionType.ASSIGN);
            query.setParameter("start", window.previousStart());
            query.setParameter("end", window.previousEnd());
        }), builder);
        Long visits = read("urgentCare.visits", () -> count("select count(e.id) from PatientEncounter e where e.encounterDate = :day and e.encounterType = :type and " + encounterWhere(scope), scope, query -> {
            query.setParameter("day", window.previousDay());
            query.setParameter("type", EncounterType.EMERGENCY);
        }), builder);
        Long waitingList = read("urgentCare.waitingList", () -> count("select count(e.id) from PatientEncounter e where e.encounterDate = :day and e.encounterType = :type and e.status = :waitingList and " + encounterWhere(scope), scope, query -> {
            query.setParameter("day", window.previousDay());
            query.setParameter("type", EncounterType.EMERGENCY);
            query.setParameter("waitingList", TreatmentStatus.WAITING_LIST);
        }), builder);
        Long inTriage = read("urgentCare.triage", () -> count("select count(e.id) from PatientEncounter e where e.encounterDate = :day and e.encounterType = :type and e.status in :triage and " + encounterWhere(scope), scope, query -> {
            query.setParameter("day", window.previousDay());
            query.setParameter("type", EncounterType.EMERGENCY);
            query.setParameter("triage", IN_TRIAGE);
        }), builder);
        Long inDepartment = read("urgentCare.inDepartment", () -> count("select count(e.id) from PatientEncounter e where e.encounterType = :type and e.status in :active and " + encounterWhere(scope), scope, query -> {
            query.setParameter("type", EncounterType.EMERGENCY);
            query.setParameter("active", IN_DEPARTMENT);
        }), builder);
        Long discharged = read("urgentCare.discharged", () -> count("select count(e.id) from PatientEncounter e where e.encounterType = :type and e.dischargeAt >= :start and e.dischargeAt < :end and " + encounterWhere(scope), scope, query -> {
            query.setParameter("type", EncounterType.EMERGENCY);
            query.setParameter("start", LocalDateTime.ofInstant(window.previousStart(), window.zone()));
            query.setParameter("end", LocalDateTime.ofInstant(window.previousEnd(), window.zone()));
        }), builder);
        Long occupied = read("urgentCare.occupied", () -> count("select count(distinct b.bedId) from EncounterAssignToBed b join b.encounter e where e.encounterType = :type and b.assignedAt <= :point and (b.releasedAt is null or b.releasedAt > :point) and " + encounterWhere(scope), scope, query -> {
            query.setParameter("type", EncounterType.EMERGENCY);
            query.setParameter("point", window.occupancyAt());
        }), builder);
        StayStats dischargedStay = read("urgentCare.dischargedStay", () -> stays(scope, "e.encounterType = :type and e.dischargeAt >= :start and e.dischargeAt < :end", window, true), builder);
        StayStats currentStay = read("urgentCare.currentStay", () -> stays(scope, "e.encounterType = :type and e.status in :active", window, false), builder);
        return new DailyBriefingFacts.UrgentCareSnapshot(true, admissions, visits, waitingList, inTriage, inDepartment, discharged, occupied,
                dischargedStay == null ? null : dischargedStay.average(), dischargedStay == null ? null : dischargedStay.sample(), dischargedStay == null ? null : dischargedStay.excluded(),
                currentStay == null ? null : currentStay.average(), currentStay == null ? null : currentStay.sample(), currentStay == null ? null : currentStay.excluded());
    }

    private DailyBriefingFacts.ComparisonCounts loadComparison(BriefingScope scope, BriefingWindow window, DailyBriefingFacts.Builder builder) {
        Long completed = read("comparison.encounters", () -> count("select count(e.id) from PatientEncounter e where e.encounterDate = :day and e.status in :completed and " + encounterWhere(scope), scope, query -> {
            query.setParameter("day", window.comparisonDay());
            query.setParameter("completed", COMPLETED);
        }), builder);
        Long patients = read("comparison.patients", () -> count("select count(distinct e.patient.id) from PatientEncounter e where e.encounterDate = :day and e.status in :completed and " + encounterWhere(scope), scope, query -> {
            query.setParameter("day", window.comparisonDay());
            query.setParameter("completed", COMPLETED);
        }), builder);
        Long cancelled = read("comparison.cancelled", () -> count("select count(e.id) from PatientEncounter e where e.encounterDate = :day and e.status = :cancelled and " + encounterWhere(scope), scope, query -> {
            query.setParameter("day", window.comparisonDay());
            query.setParameter("cancelled", TreatmentStatus.CANCELLED);
        }), builder);
        List<DailyBriefingFacts.StatusCount> appointments = read("comparison.appointments", () -> statusCounts(scope, window.comparisonStart(), window.comparisonEnd()), builder);
        Long noShows = appointments == null ? null : DailyBriefingCalculator.sum(appointments, AppointmentStatus.NO_SHOW.name()::equals);
        Long eligible = appointments == null ? null : DailyBriefingCalculator.sum(appointments, status -> {
            try {
                return DailyBriefingCalculator.countsAsEligibleAppointment(AppointmentStatus.valueOf(status));
            } catch (IllegalArgumentException ex) {
                return false;
            }
        });
        List<DailyBriefingFacts.MoneyRow> gross = read("comparison.gross", () -> money("select c.currency, sum(c.grossAmount), count(c.id) from BillingCharge c join c.encounter e where c.chargeDate >= :start and c.chargeDate < :end and c.status not in :inactive and " + encounterWhere(scope) + " group by c.currency", scope, new BriefingWindow(window.zone(), window.currentDay(), window.comparisonDay(), window.comparisonDay(), window.comparisonStart(), window.comparisonEnd(), window.comparisonStart(), window.comparisonEnd(), window.comparisonStart(), window.comparisonEnd(), window.refreshedAt(), window.occupancyAt(), window.turnaroundMinutes(), window.openEncounterHours()), query -> query.setParameter("inactive", INACTIVE_CHARGES)), builder);
        return new DailyBriefingFacts.ComparisonCounts(completed, patients, cancelled, noShows, eligible, gross);
    }

    private List<DailyBriefingFacts.EncounterRow> encounterRows(BriefingScope scope, String extra, java.util.function.Consumer<Query> bind) {
        Query query = entityManager.createQuery("""
                select e.id, p.id, e.departmentId, d.name, e.practitionerId, e.encounterReason, e.status, e.billingStatus,
                       e.encounterType, e.encounterDate, e.encounterTime, e.startedDate, e.dischargeAt,
                       case when e.chiefComplaint is null or e.chiefComplaint = '' then false else true end,
                       a.checkedInAt
                from PatientEncounter e
                join e.patient p
                join e.appointment a
                left join Department d on d.id = e.departmentId
                where %s and %s
                """.formatted(extra, encounterWhere(scope)));
        bindScope(query, scope);
        bind.accept(query);
        List<DailyBriefingFacts.EncounterRow> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            rows.add(new DailyBriefingFacts.EncounterRow(
                    asLong(values[0]), asLong(values[1]), asLong(values[2]), asString(values[3]), asLong(values[4]),
                    asEnum(com.dazzle.asklepios.domain.enumeration.EncounterReason.class, values[5]),
                    asEnum(TreatmentStatus.class, values[6]),
                    asEnum(EncounterBillingStatus.class, values[7]),
                    asEnum(EncounterType.class, values[8]),
                    (LocalDate) values[9], (LocalTime) values[10], (Instant) values[11], (LocalDateTime) values[12],
                    asBoolean(values[13]), (Instant) values[14]
            ));
        }
        return rows;
    }

    private List<DailyBriefingFacts.StatusCount> statusCounts(BriefingScope scope, Instant start, Instant end) {
        Query query = entityManager.createQuery("select a.status, count(a.id) from Appointment a where a.patient is not null and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope) + " group by a.status");
        bindScope(query, scope);
        query.setParameter("start", start);
        query.setParameter("end", end);
        List<DailyBriefingFacts.StatusCount> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            rows.add(new DailyBriefingFacts.StatusCount(String.valueOf(values[0]), asLong(values[1])));
        }
        return rows;
    }

    private List<DailyBriefingFacts.DimensionCount> departmentCounts(BriefingScope scope, Instant start, Instant end) {
        Query query = entityManager.createQuery("select a.departmentId, d.name, a.status, count(a.id) from Appointment a left join Department d on d.id = a.departmentId where a.patient is not null and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope) + " group by a.departmentId, d.name, a.status");
        bindScope(query, scope);
        query.setParameter("start", start);
        query.setParameter("end", end);
        List<DailyBriefingFacts.DimensionCount> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            rows.add(new DailyBriefingFacts.DimensionCount(values[0] == null ? "UNKNOWN" : values[0].toString(), asString(values[1]), String.valueOf(values[2]), asLong(values[3])));
        }
        return rows;
    }

    private List<DailyBriefingFacts.StatusCount> serviceCounts(BriefingScope scope, Instant start, Instant end) {
        Query query = entityManager.createQuery("select a.service, count(a.id) from Appointment a where a.patient is not null and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope) + " group by a.service");
        bindScope(query, scope);
        query.setParameter("start", start);
        query.setParameter("end", end);
        List<DailyBriefingFacts.StatusCount> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            rows.add(new DailyBriefingFacts.StatusCount(values[0] == null ? "UNKNOWN" : String.valueOf(values[0]), asLong(values[1])));
        }
        return rows;
    }

    private List<DailyBriefingFacts.AppointmentRow> appointmentRows(BriefingScope scope, Instant start, Instant end) {
        Query query = entityManager.createQuery("select a.id, a.status, a.startDatetime, a.endDatetime, a.defaultPractitionerId, a.resourceType, a.resourceId, a.requirePractitioner from Appointment a where a.patient is not null and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope));
        bindScope(query, scope);
        query.setParameter("start", start);
        query.setParameter("end", end);
        List<DailyBriefingFacts.AppointmentRow> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            Long practitioner = asLong(values[4]);
            if (practitioner == null && values[5] == TemplateType.PRACTITIONER) {
                practitioner = asLong(values[6]);
            }
            rows.add(new DailyBriefingFacts.AppointmentRow(asLong(values[0]), asEnum(AppointmentStatus.class, values[1]), (Instant) values[2], (Instant) values[3], practitioner, Boolean.TRUE.equals(values[7])));
        }
        return rows;
    }

    private List<DailyBriefingFacts.ChargeRow> chargeRows(BriefingScope scope, BriefingWindow window) {
        Query query = entityManager.createQuery("select c.currency, sum(c.grossAmount), sum(c.discountAmount), sum(c.exemptionAmount), sum(c.netAmount), count(c.id) from BillingCharge c join c.encounter e where c.chargeDate >= :start and c.chargeDate < :end and c.status not in :inactive and " + encounterWhere(scope) + " group by c.currency");
        bindScope(query, scope);
        query.setParameter("start", window.previousStart());
        query.setParameter("end", window.previousEnd());
        query.setParameter("inactive", INACTIVE_CHARGES);
        List<DailyBriefingFacts.ChargeRow> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            rows.add(new DailyBriefingFacts.ChargeRow(String.valueOf(values[0]), asMoney(values[1]), asMoney(values[2]), asMoney(values[3]), asMoney(values[4]), asLong(values[5])));
        }
        return rows;
    }

    private List<DailyBriefingFacts.MoneyRow> documents(BriefingScope scope, BriefingWindow window, FinancialDocumentType type) {
        Query query = entityManager.createQuery("select d.currency, sum(d.totalAmount), count(d.id) from FinancialDocument d, PatientEncounter e where d.encounterId = e.id and d.documentType = :type and d.status not in :inactive and d.createdDate >= :start and d.createdDate < :end and " + encounterWhere(scope) + " group by d.currency");
        bindScope(query, scope);
        query.setParameter("type", type);
        query.setParameter("inactive", INACTIVE_DOCUMENTS);
        query.setParameter("start", window.previousStart());
        query.setParameter("end", window.previousEnd());
        return moneyResult(query.getResultList(), false);
    }

    private List<DailyBriefingFacts.AuthRow> authRows(BriefingScope scope) {
        Query query = entityManager.createQuery("select p.status, p.outcome, p.payorId, p.dateOrdered, count(p.id), coalesce(sum(p.totalNet), 0) from PreAuthorizationRequest p, PatientEncounter e where p.encounterId = e.id and p.isCancelled = false and " + encounterWhere(scope) + " group by p.status, p.outcome, p.payorId, p.dateOrdered");
        bindScope(query, scope);
        List<DailyBriefingFacts.AuthRow> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            rows.add(new DailyBriefingFacts.AuthRow(asString(values[0]), asString(values[1]), asLong(values[2]), (LocalDate) values[3], asLong(values[4]), asMoney(values[5])));
        }
        return rows;
    }

    private List<DailyBriefingFacts.ClaimRow> claimRows(BriefingScope scope, Instant start, Instant end) {
        String submitted = start == null ? "" : " and c.submittedAt >= :start and c.submittedAt < :end and c.status in :submitted";
        Query query = entityManager.createQuery("select c.status, c.outcome, i.payorId, i.payerName, count(c.id), coalesce(sum(c.totalNet), 0) from ClaimRequest c join PatientEncounter e on e.id = c.encounterId left join PatientInsurance i on i.id = c.patientInsuranceId where " + encounterWhere(scope) + submitted + " group by c.status, c.outcome, i.payorId, i.payerName");
        bindScope(query, scope);
        if (start != null) {
            query.setParameter("start", start);
            query.setParameter("end", end);
            query.setParameter("submitted", SUBMITTED);
        }
        List<DailyBriefingFacts.ClaimRow> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            rows.add(new DailyBriefingFacts.ClaimRow(String.valueOf(values[0]), asString(values[1]), asLong(values[2]), asString(values[3]), asLong(values[4]), asMoney(values[5])));
        }
        return rows;
    }

    private List<DailyBriefingFacts.TatSample> turnaround(BriefingScope scope, BriefingWindow window) {
        Query query = entityManager.createQuery("select t.orderType, t.patientArrivedDate, t.submitDate, t.approvedDate, t.readyDate from DiagnosticOrderTest t, DiagnosticOrder o, PatientEncounter e where t.orderId = o.id and o.encounterId = e.id and ((t.approvedDate >= :start and t.approvedDate < :end) or (t.approvedDate is null and t.readyDate >= :start and t.readyDate < :end)) and " + encounterWhere(scope));
        bindScope(query, scope);
        query.setParameter("start", window.previousStart());
        query.setParameter("end", window.previousEnd());
        query.setMaxResults(MAX_ROWS);
        List<DailyBriefingFacts.TatSample> samples = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            Instant end = (Instant) (values[3] != null ? values[3] : values[4]);
            Instant start = (Instant) (values[1] != null ? values[1] : values[2]);
            if (start == null || end == null || end.isBefore(start)) {
                continue;
            }
            samples.add(new DailyBriefingFacts.TatSample(String.valueOf(values[0]), java.time.Duration.between(start, end).toMinutes()));
        }
        return samples;
    }

    private StayStats stays(BriefingScope scope, String extra, BriefingWindow window, boolean discharged) {
        Query query = entityManager.createQuery("select e.startedDate, e.dischargeAt, e.encounterDate, e.encounterTime from PatientEncounter e where " + extra + " and " + encounterWhere(scope));
        bindScope(query, scope);
        query.setParameter("type", EncounterType.EMERGENCY);
        if (discharged) {
            query.setParameter("start", LocalDateTime.ofInstant(window.previousStart(), window.zone()));
            query.setParameter("end", LocalDateTime.ofInstant(window.previousEnd(), window.zone()));
        } else {
            query.setParameter("active", IN_DEPARTMENT);
        }
        List<BigDecimal> hours = new ArrayList<>();
        int excluded = 0;
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            Instant start = (Instant) values[0];
            if (start == null && values[2] != null) {
                LocalTime time = values[3] == null ? LocalTime.MIN : (LocalTime) values[3];
                start = ((LocalDate) values[2]).atTime(time).atZone(window.zone()).toInstant();
            }
            Instant end = discharged && values[1] != null ? ((LocalDateTime) values[1]).atZone(window.zone()).toInstant() : window.refreshedAt();
            BigDecimal stay = DailyBriefingCalculator.hoursBetween(start, end);
            if (stay == null) {
                excluded++;
            } else {
                hours.add(stay);
            }
        }
        return new StayStats(DailyBriefingCalculator.average(hours), hours.size(), excluded);
    }

    private List<DailyBriefingFacts.MoneyRow> money(String jpql, BriefingScope scope, BriefingWindow window, java.util.function.Consumer<Query> extra) {
        Query query = entityManager.createQuery(jpql);
        bindScope(query, scope);
        if (window != null && jpql.contains(":start")) {
            query.setParameter("start", window.previousStart());
            query.setParameter("end", window.previousEnd());
        }
        if (extra != null) {
            extra.accept(query);
        }
        return moneyResult(query.getResultList(), countCommas(jpql));
    }

    private List<DailyBriefingFacts.MoneyRow> moneyResult(List<?> rows, boolean labeled) {
        List<DailyBriefingFacts.MoneyRow> result = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            if (labeled && values.length >= 4) {
                result.add(new DailyBriefingFacts.MoneyRow(String.valueOf(values[0]), asMoney(values[2]), asLong(values[3]), String.valueOf(values[1])));
            } else {
                result.add(new DailyBriefingFacts.MoneyRow(String.valueOf(values[0]), asMoney(values[1]), asLong(values[2]), null));
            }
        }
        return result;
    }

    private List<DailyBriefingCase> encounterCases(BriefingScope scope, String extra, LocalDate day, List<TreatmentStatus> statuses, int limit) {
        return encounterCases(scope, extra, day, statuses, limit, null);
    }

    private List<DailyBriefingCase> encounterCases(BriefingScope scope, String extra, LocalDate day, List<TreatmentStatus> statuses, int limit, BriefingWindow window) {
        Query query = entityManager.createQuery("select e.id, p.id, e.departmentId, e.practitionerId, e.status, e.startedDate from PatientEncounter e join e.patient p where " + extra + " and " + encounterWhere(scope) + " order by e.id");
        bindScope(query, scope);
        if (day != null) {
            query.setParameter("day", day);
        }
        if (statuses != null) {
            query.setParameter("statuses", statuses);
        }
        if (extra.contains(":cancelled")) {
            query.setParameter("cancelled", TreatmentStatus.CANCELLED);
        }
        if (extra.contains(":billing")) {
            query.setParameter("billing", EncounterBillingStatus.OPEN);
        }
        if (extra.contains(":waitingList")) {
            query.setParameter("waitingList", TreatmentStatus.WAITING_LIST);
        }
        if (extra.contains(":emergency")) {
            query.setParameter("emergency", EncounterType.EMERGENCY);
        }
        if (window != null && extra.contains(":cutoff")) {
            query.setParameter("cutoff", window.refreshedAt().minusSeconds(window.openEncounterHours() * 3600L));
            query.setParameter("oldest", window.currentDay().minusDays(1));
        }
        if (window != null && extra.contains(":rangeStart")) {
            query.setParameter("rangeStart", LocalDateTime.ofInstant(window.previousStart(), window.zone()));
            query.setParameter("rangeEnd", LocalDateTime.ofInstant(window.previousEnd(), window.zone()));
        }
        query.setMaxResults(limit);
        return caseRows(query.getResultList(), "ENCOUNTER");
    }

    private List<DailyBriefingCase> appointmentCases(BriefingScope scope, String extra, Instant start, Instant end, int limit) {
        Query query = entityManager.createQuery("select a.id, p.id, a.departmentId, a.defaultPractitionerId, a.status, a.startDatetime from Appointment a join a.patient p where " + extra + " and a.startDatetime >= :start and a.startDatetime < :end and " + appointmentWhere(scope) + " order by a.id");
        bindScope(query, scope);
        query.setParameter("start", start);
        query.setParameter("end", end);
        if (extra.contains(":cancelledAppointment")) {
            query.setParameter("cancelledAppointment", AppointmentStatus.CANCELLED);
        }
        if (extra.contains(":noShow")) {
            query.setParameter("noShow", AppointmentStatus.NO_SHOW);
        }
        if (extra.contains(":practitionerType")) {
            query.setParameter("practitionerType", TemplateType.PRACTITIONER);
            query.setParameter("offBook", OFF_BOOK);
        }
        query.setMaxResults(limit);
        return caseRows(query.getResultList(), "APPOINTMENT");
    }

    private List<DailyBriefingCase> conflictCases(BriefingScope scope, BriefingWindow window, int limit) {
        List<DailyBriefingFacts.AppointmentRow> booked = appointmentRows(scope, window.currentStart(), window.currentEnd());
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
        Query query = entityManager.createQuery("select p.id, p.patientId, e.departmentId, e.practitionerId, p.status, p.totalNet from PreAuthorizationRequest p, PatientEncounter e where p.encounterId = e.id and p.isCancelled = false and " + encounterWhere(scope) + " order by p.id");
        bindScope(query, scope);
        query.setMaxResults(MAX_ROWS);
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            boolean denial = DailyBriefingCalculator.isAuthorizationDenial(asString(values[4]), null);
            boolean pending = DailyBriefingCalculator.isClassifiedPendingAuthorization(asString(values[4]), null) || DailyBriefingCalculator.isUnclassifiedOpenAuthorization(asString(values[4]), null);
            if (drill == Drill.AUTHORIZATION_DENIALS && !denial) {
                continue;
            }
            if (drill == Drill.PENDING_AUTHORIZATIONS && !pending) {
                continue;
            }
            cases.add(new DailyBriefingCase(asLong(values[0]), "AUTHORIZATION", asLong(values[1]), asLong(values[2]), asLong(values[3]), asString(values[4]), null, asMoney(values[5]), null, null));
            if (cases.size() >= limit) {
                break;
            }
        }
        return cases;
    }

    private List<DailyBriefingCase> holdCases(BriefingScope scope, int limit) {
        Query query = entityManager.createQuery("select r.id, r.patient.id, e.departmentId, e.practitionerId, r.preAuthorizationStatus, r.responsibilityAmount, r.currency from BillingChargeResponsibility r join r.charge c join r.encounter e where r.preAuthorizationRequired = true and r.preAuthorizationStatus = :pending and r.supersededResponsibility is null and c.status not in :inactive and " + encounterWhere(scope) + " order by r.id");
        bindScope(query, scope);
        query.setParameter("pending", PreAuthorizationStatus.PENDING_APPROVAL);
        query.setParameter("inactive", INACTIVE_CHARGES);
        query.setMaxResults(limit);
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            cases.add(new DailyBriefingCase(asLong(values[0]), "SERVICE", asLong(values[1]), asLong(values[2]), asLong(values[3]), String.valueOf(values[4]), null, asMoney(values[5]), String.valueOf(values[6]), null));
        }
        return cases;
    }

    private List<DailyBriefingCase> claimCases(BriefingScope scope, ClaimStatus status, int limit) {
        Query query = entityManager.createQuery("select c.id, c.patientId, e.departmentId, e.practitionerId, c.status, c.totalNet, c.submittedAt from ClaimRequest c, PatientEncounter e where c.encounterId = e.id and c.status = :status and " + encounterWhere(scope) + " order by c.id");
        bindScope(query, scope);
        query.setParameter("status", status);
        query.setMaxResults(limit);
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            cases.add(new DailyBriefingCase(asLong(values[0]), "CLAIM", asLong(values[1]), asLong(values[2]), asLong(values[3]), String.valueOf(values[4]), (Instant) values[6], asMoney(values[5]), null, null));
        }
        return cases;
    }

    private List<DailyBriefingCase> orderCases(BriefingScope scope, BriefingWindow window, int limit) {
        Query query = entityManager.createQuery("select t.id, e.patient.id, t.receivedDepartmentId, e.practitionerId, t.processingStatus, t.createdDate from DiagnosticOrderTest t, DiagnosticOrder o, PatientEncounter e where t.orderId = o.id and o.encounterId = e.id and t.processingStatus not in :done and t.createdDate < :cutoff and " + encounterWhere(scope) + " order by t.createdDate");
        bindScope(query, scope);
        query.setParameter("done", ORDER_DONE);
        query.setParameter("cutoff", window.refreshedAt().minusSeconds(window.turnaroundMinutes() * 60L));
        query.setMaxResults(limit);
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            Instant created = (Instant) values[5];
            Long age = created == null ? null : java.time.Duration.between(created, window.refreshedAt()).toHours();
            cases.add(new DailyBriefingCase(asLong(values[0]), "ORDER", asLong(values[1]), asLong(values[2]), asLong(values[3]), String.valueOf(values[4]), created, null, null, age));
        }
        return cases;
    }

    private List<DailyBriefingCase> admissionCases(BriefingScope scope, BriefingWindow window, int limit) {
        Query query = entityManager.createQuery("select e.id, p.id, e.departmentId, e.practitionerId, e.status, e.startedDate from PatientEncounter e join e.patient p where e.encounterType = :emergency and exists (select 1 from BedTransaction b where b.encounter = e and b.transactionType = :assign and b.transactionDate >= :start and b.transactionDate < :end) and " + encounterWhere(scope) + " order by e.id");
        bindScope(query, scope);
        query.setParameter("emergency", EncounterType.EMERGENCY);
        query.setParameter("assign", BedTransactionType.ASSIGN);
        query.setParameter("start", window.previousStart());
        query.setParameter("end", window.previousEnd());
        query.setMaxResults(limit);
        return caseRows(query.getResultList(), "ENCOUNTER");
    }

    private List<DailyBriefingCase> caseRows(List<?> rows, String type) {
        List<DailyBriefingCase> cases = new ArrayList<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            cases.add(new DailyBriefingCase(asLong(values[0]), type, asLong(values[1]), asLong(values[2]), asLong(values[3]), String.valueOf(values[4]), values[5] instanceof Instant instant ? instant : null, null, null, null));
        }
        return cases;
    }

    private String encounterWhere(BriefingScope scope) {
        StringBuilder where = new StringBuilder("e.facilityId = :facilityId");
        if (scope.departmentId() != null) {
            where.append(" and e.departmentId = :departmentId");
        }
        if (scope.practitionerId() != null) {
            where.append(" and e.practitionerId = :practitionerId");
        }
        if (scope.practitionerIds() != null) {
            where.append(" and e.practitionerId in :practitionerIds");
        }
        return where.toString();
    }

    private String appointmentWhere(BriefingScope scope) {
        StringBuilder where = new StringBuilder("a.facilityId = :facilityId");
        if (scope.departmentId() != null) {
            where.append(" and a.departmentId = :departmentId");
        }
        if (scope.practitionerId() != null) {
            where.append(" and (a.defaultPractitionerId = :practitionerId or (a.resourceType = :practitionerType and a.resourceId = :practitionerId))");
        }
        if (scope.practitionerIds() != null) {
            where.append(" and (a.defaultPractitionerId in :practitionerIds or (a.resourceType = :practitionerType and a.resourceId in :practitionerIds))");
        }
        return where.toString();
    }

    private String department(String alias, Long departmentId) {
        return departmentId == null ? "" : " and " + alias + ".departmentId = :departmentId";
    }

    private void bindScope(Query query, BriefingScope scope) {
        query.setParameter("facilityId", scope.facilityId());
        if (scope.departmentId() != null) {
            query.setParameter("departmentId", scope.departmentId());
        }
        if (scope.practitionerId() != null) {
            query.setParameter("practitionerId", scope.practitionerId());
        }
        if (scope.practitionerIds() != null) {
            query.setParameter("practitionerIds", scope.practitionerIds());
        }
        if (query.getParameters().stream().anyMatch(parameter -> "practitionerType".equals(parameter.getName())) && scope.practitionerId() != null || scope.practitionerIds() != null) {
            if (query.getParameters().stream().anyMatch(parameter -> "practitionerType".equals(parameter.getName()))) {
                query.setParameter("practitionerType", TemplateType.PRACTITIONER);
            }
        }
    }

    private long count(String jpql, BriefingScope scope, java.util.function.Consumer<Query> extra) {
        Query query = entityManager.createQuery(jpql);
        bindScope(query, scope);
        if (extra != null) {
            extra.accept(query);
        }
        return asLong(query.getSingleResult());
    }

    private List<Long> longs(String jpql, Long facilityId, Long departmentId, java.util.function.Consumer<Query> extra) {
        Query query = entityManager.createQuery(jpql);
        query.setParameter("facilityId", facilityId);
        if (departmentId != null) {
            query.setParameter("departmentId", departmentId);
        }
        extra.accept(query);
        List<Long> values = new ArrayList<>();
        for (Object row : query.getResultList()) {
            values.add(asLong(row));
        }
        return values;
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

    private static boolean countCommas(String jpql) {
        int select = jpql.indexOf("select ");
        int from = jpql.indexOf(" from ");
        if (select < 0 || from <= select) {
            return false;
        }
        return jpql.substring(select, from).chars().filter(ch -> ch == ',').count() >= 3;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static BigDecimal asMoney(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal money) {
            return money;
        }
        return new BigDecimal(value.toString());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }

    private static <E extends Enum<E>> E asEnum(Class<E> type, Object value) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return Enum.valueOf(type, value.toString());
    }

    private record StayStats(BigDecimal average, int sample, int excluded) {
    }
}
