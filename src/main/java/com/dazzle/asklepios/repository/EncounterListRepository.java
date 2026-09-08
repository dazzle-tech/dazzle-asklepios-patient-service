package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface EncounterListRepository extends JpaRepository<PatientEncounter, Long> {

    @Query("""
        select pe
        from PatientEncounter pe
        join fetch pe.patient p
        left join fetch pe.appointment a

        where pe.facilityId = :facilityId
          and pe.encounterDate between :fromDate and :toDate

          and (
                coalesce(:patientName, '') = ''
                or lower(coalesce(p.firstName, '')) like lower(
                    concat('%', coalesce(:patientName, ''), '%')
                )
                or lower(coalesce(p.secondName, '')) like lower(
                    concat('%', coalesce(:patientName, ''), '%')
                )
                or lower(coalesce(p.thirdName, '')) like lower(
                    concat('%', coalesce(:patientName, ''), '%')
                )
                or lower(coalesce(p.lastName, '')) like lower(
                    concat('%', coalesce(:patientName, ''), '%')
                )
              )

          and (
                coalesce(:mrn, '') = ''
                or lower(coalesce(p.medicalRecordNumber, '')) like lower(
                    concat('%', coalesce(:mrn, ''), '%')
                )
              )

          and (
                :departmentId is null
                or pe.departmentId = :departmentId
              )

          and (
                :practitionerId is null
                or pe.practitionerId = :practitionerId
              )

          and (
                :coverageType is null
                or pe.coverageType = :coverageType
              )
            and (
              :paymentStatus is null
              or exists (
                  select 1
                  from PatientServiceAndProduct psp
                  where psp.encounterId = pe.id
                    and psp.paymentStatus = :paymentStatus
              )
            )
          and (
                coalesce(:insuranceName, '') = ''
                or exists (
                    select 1
                    from PatientInsurance pi
                    where pi.patient.id = p.id
                      and lower(coalesce(pi.payerName, '')) like lower(
                          concat('%', coalesce(:insuranceName, ''), '%')
                      )
                )
              )

          and (
                :encounterType is null
                or pe.encounterType = :encounterType
              )

          and (
                coalesce(:encounterNumber, '') = ''
                or lower(coalesce(pe.encounterNumber, '')) like lower(
                    concat('%', coalesce(:encounterNumber, ''), '%')
                )
              )

          and (
                :encounterStatuses is null
                or pe.encounterStatus in :encounterStatuses
              )

          and (
                :treatmentStatuses is null
                or pe.status in :treatmentStatuses
              )

          and (
                :encounterReasons is null
                or pe.encounterReason in :encounterReasons
              )

          and (
                :doctorStartedFromEnabled = false
                or (
                    pe.startedDate is not null
                    and pe.startedDate >= :doctorStartedFrom
                )
              )

          and (
                :doctorStartedToEnabled = false
                or (
                    pe.startedDate is not null
                    and pe.startedDate <= :doctorStartedTo
                )
              )
        """)
    Page<PatientEncounter> search(
            @Param("facilityId")
            Long facilityId,

            @Param("fromDate")
            LocalDate fromDate,

            @Param("toDate")
            LocalDate toDate,

            @Param("patientName")
            String patientName,

            @Param("mrn")
            String mrn,

            @Param("departmentId")
            Long departmentId,

            @Param("practitionerId")
            Long practitionerId,

            @Param("coverageType")
            BillingCoverageType coverageType,

            @Param("paymentStatus")
            PaymentStatus paymentStatus,

            @Param("insuranceName")
            String insuranceName,

            @Param("encounterType")
            EncounterType encounterType,

            @Param("encounterNumber")
            String encounterNumber,

            @Param("encounterStatuses")
            List<EncounterStatus> encounterStatuses,

            @Param("treatmentStatuses")
            List<TreatmentStatus> treatmentStatuses,

            @Param("encounterReasons")
            List<EncounterReason> encounterReasons,

            @Param("doctorStartedFromEnabled")
            boolean doctorStartedFromEnabled,

            @Param("doctorStartedToEnabled")
            boolean doctorStartedToEnabled,

            @Param("doctorStartedFrom")
            Instant doctorStartedFrom,

            @Param("doctorStartedTo")
            Instant doctorStartedTo,

            Pageable pageable
    );
}