package com.dazzle.asklepios.service.dailybriefing;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Query results for one briefing. A null list or null number means that read failed.
 * An empty list or zero means the read succeeded and the value is a true zero.
 */
public record DailyBriefingFacts(
        List<EncounterRow> previousEncounters,
        boolean previousEncountersCapped,
        Long previousEncounterTotal,
        List<EncounterRow> openEncounters,
        boolean openEncountersCapped,
        Long openEncounterTotal,
        ComparisonCounts comparison,
        List<StatusCount> currentAppointmentStatuses,
        List<DimensionCount> currentAppointmentsByDepartment,
        List<StatusCount> currentAppointmentsByService,
        Long currentOpenSlots,
        List<AppointmentRow> currentBooked,
        boolean currentBookedCapped,
        Long bookedWithoutPractitioner,
        List<StatusCount> previousAppointmentStatuses,
        BillingSnapshot billing,
        List<AuthRow> authorizations,
        Long servicesOnHoldCount,
        List<MoneyRow> servicesOnHold,
        List<ClaimRow> claims,
        List<ClaimRow> claimsSubmittedYesterday,
        PatientInsuranceSnapshot patientInsurance,
        OrderSnapshot orders,
        UrgentCareSnapshot urgentCare,
        Long unbilledEncountersWithActiveCharge,
        List<MoneyRow> unbilledCharges,
        List<String> failures
) {

    public static Builder builder() {
        return new Builder();
    }

    public record EncounterRow(
            Long id,
            Long patientId,
            Long departmentId,
            String departmentName,
            Long practitionerId,
            EncounterReason reason,
            TreatmentStatus status,
            EncounterBillingStatus billingStatus,
            EncounterType encounterType,
            LocalDate encounterDate,
            LocalTime encounterTime,
            Instant startedDate,
            LocalDateTime dischargeAt,
            boolean chiefComplaintPresent,
            Instant checkedInAt
    ) {
    }

    public record AppointmentRow(
            Long id,
            AppointmentStatus status,
            Instant start,
            Instant end,
            Long practitionerId,
            boolean requirePractitioner
    ) {
    }

    public record StatusCount(String status, long count) {
    }

    public record DimensionCount(String key, String label, String status, long count) {
    }

    public record MoneyRow(String currency, BigDecimal amount, long count, String label) {
    }

    public record ChargeRow(
            String currency,
            BigDecimal gross,
            BigDecimal discount,
            BigDecimal exemption,
            BigDecimal net,
            long count
    ) {
    }

    public record BillingSnapshot(
            List<ChargeRow> activeCharges,
            List<MoneyRow> cancellations,
            Long reversedWithoutTimestamp,
            List<MoneyRow> billingPayments,
            List<MoneyRow> classifiedRefunds,
            Long unclassifiedRefunds,
            List<MoneyRow> responsibilities,
            List<MoneyRow> issuedInvoices
    ) {
    }

    public record AuthRow(
            String status,
            String outcome,
            Long payorId,
            LocalDate orderedOn,
            long count,
            BigDecimal requestedNet
    ) {
    }

    public record ClaimRow(
            String status,
            String outcome,
            Long payorId,
            String payerName,
            long count,
            BigDecimal claimedNet
    ) {
    }

    public record PatientInsuranceSnapshot(
            Long insuredPatients,
            Long insuranceRecords,
            Long withBenefitRules,
            Long missingBenefitRules
    ) {
    }

    public record TatSample(String orderType, long minutes) {
    }

    public record OrderSnapshot(
            List<TatSample> turnaround,
            boolean turnaroundCapped,
            long turnaroundMissingStart,
            Long overdueOrders,
            Long withinCycleOrders,
            Long ordersMissingAge
    ) {
    }

    public record UrgentCareSnapshot(
            Boolean moduleUsed,
            Long admissions,
            Long visits,
            Long waitingList,
            Long inTriage,
            Long inDepartment,
            Long discharged,
            Long occupiedBeds,
            BigDecimal dischargedStayHours,
            Integer dischargedStaySample,
            Integer dischargedStayExcluded,
            BigDecimal currentStayHours,
            Integer currentStaySample,
            Integer currentStayExcluded
    ) {
    }

    public record ComparisonCounts(
            Long completedEncounters,
            Long uniquePatients,
            Long cancelledEncounters,
            Long noShows,
            Long eligibleAppointments,
            List<MoneyRow> gross
    ) {
    }

    public static final class Builder {
        private List<EncounterRow> previousEncounters = List.of();
        private boolean previousEncountersCapped;
        private Long previousEncounterTotal;
        private List<EncounterRow> openEncounters = List.of();
        private boolean openEncountersCapped;
        private Long openEncounterTotal;
        private ComparisonCounts comparison = new ComparisonCounts(0L, 0L, 0L, 0L, 0L, List.of());
        private List<StatusCount> currentAppointmentStatuses = List.of();
        private List<DimensionCount> currentAppointmentsByDepartment = List.of();
        private List<StatusCount> currentAppointmentsByService = List.of();
        private Long currentOpenSlots = 0L;
        private List<AppointmentRow> currentBooked = List.of();
        private boolean currentBookedCapped;
        private Long bookedWithoutPractitioner = 0L;
        private List<StatusCount> previousAppointmentStatuses = List.of();
        private BillingSnapshot billing = emptyBilling();
        private List<AuthRow> authorizations = List.of();
        private Long servicesOnHoldCount = 0L;
        private List<MoneyRow> servicesOnHold = List.of();
        private List<ClaimRow> claims = List.of();
        private List<ClaimRow> claimsSubmittedYesterday = List.of();
        private PatientInsuranceSnapshot patientInsurance = new PatientInsuranceSnapshot(0L, 0L, 0L, 0L);
        private OrderSnapshot orders = new OrderSnapshot(List.of(), false, 0, 0L, 0L, 0L);
        private UrgentCareSnapshot urgentCare = new UrgentCareSnapshot(false, null, null, null, null, null, null, null, null, null, null, null, null, null);
        private Long unbilledEncountersWithActiveCharge = 0L;
        private List<MoneyRow> unbilledCharges = List.of();
        private final List<String> failures = new ArrayList<>();

        public Builder previousEncounters(List<EncounterRow> rows) {
            this.previousEncounters = rows;
            return this;
        }

        public Builder previousEncountersCapped(boolean capped, Long total) {
            this.previousEncountersCapped = capped;
            this.previousEncounterTotal = total;
            if (capped) {
                this.previousEncounters = null;
            }
            return this;
        }

        public Builder openEncounters(List<EncounterRow> rows) {
            this.openEncounters = rows;
            return this;
        }

        public Builder openEncountersCapped(boolean capped, Long total) {
            this.openEncountersCapped = capped;
            this.openEncounterTotal = total;
            if (capped) {
                this.openEncounters = null;
            }
            return this;
        }

        public Builder comparison(ComparisonCounts comparison) {
            this.comparison = comparison;
            return this;
        }

        public Builder currentAppointmentStatuses(List<StatusCount> rows) {
            this.currentAppointmentStatuses = rows;
            return this;
        }

        public Builder currentAppointmentsByDepartment(List<DimensionCount> rows) {
            this.currentAppointmentsByDepartment = rows;
            return this;
        }

        public Builder currentAppointmentsByService(List<StatusCount> rows) {
            this.currentAppointmentsByService = rows;
            return this;
        }

        public Builder currentOpenSlots(Long count) {
            this.currentOpenSlots = count;
            return this;
        }

        public Builder currentBooked(List<AppointmentRow> rows) {
            this.currentBooked = rows;
            return this;
        }

        public Builder currentBookedCapped(boolean capped) {
            this.currentBookedCapped = capped;
            if (capped) {
                this.currentBooked = null;
            }
            return this;
        }

        public Builder bookedWithoutPractitioner(Long count) {
            this.bookedWithoutPractitioner = count;
            return this;
        }

        public Builder previousAppointmentStatuses(List<StatusCount> rows) {
            this.previousAppointmentStatuses = rows;
            return this;
        }

        public Builder billing(BillingSnapshot billing) {
            this.billing = billing;
            return this;
        }

        public Builder authorizations(List<AuthRow> rows) {
            this.authorizations = rows;
            return this;
        }

        public Builder servicesOnHold(Long count, List<MoneyRow> amounts) {
            this.servicesOnHoldCount = count;
            this.servicesOnHold = amounts;
            return this;
        }

        public Builder claims(List<ClaimRow> rows) {
            this.claims = rows;
            return this;
        }

        public Builder claimsSubmittedYesterday(List<ClaimRow> rows) {
            this.claimsSubmittedYesterday = rows;
            return this;
        }

        public Builder patientInsurance(PatientInsuranceSnapshot snapshot) {
            this.patientInsurance = snapshot;
            return this;
        }

        public Builder orders(OrderSnapshot orders) {
            this.orders = orders;
            return this;
        }

        public Builder urgentCare(UrgentCareSnapshot urgentCare) {
            this.urgentCare = urgentCare;
            return this;
        }

        public Builder unbilled(Long encountersWithCharge, List<MoneyRow> charges) {
            this.unbilledEncountersWithActiveCharge = encountersWithCharge;
            this.unbilledCharges = charges;
            return this;
        }

        public Builder failure(String code) {
            this.failures.add(code);
            return this;
        }

        public DailyBriefingFacts build() {
            return new DailyBriefingFacts(
                    previousEncounters,
                    previousEncountersCapped,
                    previousEncounterTotal,
                    openEncounters,
                    openEncountersCapped,
                    openEncounterTotal,
                    comparison,
                    currentAppointmentStatuses,
                    currentAppointmentsByDepartment,
                    currentAppointmentsByService,
                    currentOpenSlots,
                    currentBooked,
                    currentBookedCapped,
                    bookedWithoutPractitioner,
                    previousAppointmentStatuses,
                    billing,
                    authorizations,
                    servicesOnHoldCount,
                    servicesOnHold,
                    claims,
                    claimsSubmittedYesterday,
                    patientInsurance,
                    orders,
                    urgentCare,
                    unbilledEncountersWithActiveCharge,
                    unbilledCharges,
                    List.copyOf(failures)
            );
        }

        private static BillingSnapshot emptyBilling() {
            return new BillingSnapshot(List.of(), List.of(), 0L, List.of(), List.of(), 0L, List.of(), List.of());
        }
    }
}
