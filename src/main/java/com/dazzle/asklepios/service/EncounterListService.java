package com.dazzle.asklepios.service;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.repository.AdditionalMeasurementsRepository;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.EncounterListRepository;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PainAssessmentRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.service.dto.patientEncounter.EncounterListFilterDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.vm.EncounterListVM;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EncounterListService {

    private final EncounterListRepository encounterListRepository;

    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientDocumentRepository patientDocumentRepository;

    private final AdditionalMeasurementsRepository additionalMeasurementsRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final PainAssessmentRepository painAssessmentRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;

    private final DepartmentHelper departmentHelper;
    private final PractitionerHelper practitionerHelper;
    private final ServiceHelper serviceHelper;

    public EncounterListService(
            EncounterListRepository encounterListRepository,
            PatientInsuranceRepository patientInsuranceRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            PatientDocumentRepository patientDocumentRepository,
            AdditionalMeasurementsRepository additionalMeasurementsRepository,
            VitalSignsRepository vitalSignsRepository,
            PainAssessmentRepository painAssessmentRepository,
            BodyMeasurementsRepository bodyMeasurementsRepository,
            PatientObservationsComplaintsRepository patientObservationsComplaintsRepository,
            DepartmentHelper departmentHelper,
            PractitionerHelper practitionerHelper,
            ServiceHelper serviceHelper
    ) {
        this.encounterListRepository = encounterListRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.patientDocumentRepository = patientDocumentRepository;

        this.additionalMeasurementsRepository = additionalMeasurementsRepository;
        this.vitalSignsRepository = vitalSignsRepository;
        this.painAssessmentRepository = painAssessmentRepository;
        this.bodyMeasurementsRepository = bodyMeasurementsRepository;
        this.patientObservationsComplaintsRepository = patientObservationsComplaintsRepository;

        this.departmentHelper = departmentHelper;
        this.practitionerHelper = practitionerHelper;
        this.serviceHelper = serviceHelper;
    }

    public Page<EncounterListVM> search(
            EncounterListFilterDTO filter,
            Pageable pageable
    ) {

        LocalDate today = LocalDate.now();

        Long facilityId =
                filter != null && filter.facilityId() != null
                        ? filter.facilityId()
                        : null;

        LocalDate fromDate =
                filter != null && filter.fromDate() != null
                        ? filter.fromDate()
                        : today;

        LocalDate toDate =
                filter != null && filter.toDate() != null
                        ? filter.toDate()
                        : today;

        if (facilityId == null) {
            throw new IllegalArgumentException("facilityId is required");
        }

        List<EncounterStatus> encounterStatuses =
                extractEncounterStatuses(filter);

        BillingCoverageType coverageType = null;

        if (filter != null && filter.coverageType() != null && !filter.coverageType().isBlank()) {
            coverageType = BillingCoverageType.valueOf(
                    filter.coverageType().trim().toUpperCase()
            );
        }

        PaymentStatus paymentStatus = null;

        if (filter != null
                && filter.paymentStatus() != null
                && !filter.paymentStatus().isBlank()) {

            paymentStatus = PaymentStatus.valueOf(
                    filter.paymentStatus().trim().toUpperCase()
            );
        }

        Page<PatientEncounter> page =
                encounterListRepository.search(
                        facilityId,
                        fromDate,
                        toDate,

                        filter != null ? filter.patientName() : null,
                        filter != null ? filter.mrn() : null,

                        filter != null ? filter.departmentId() : null,
                        filter != null ? filter.practitionerId() : null,

                        coverageType,
                        paymentStatus,
                        filter != null
                                ? filter.insuranceName()
                                : null,

                        filter != null ? filter.encounterType() : null,
                        filter != null ? filter.encounterNumber() : null,

                        encounterStatuses,

                        filter != null
                                && filter.treatmentStatusIn() != null
                                && !filter.treatmentStatusIn().isEmpty()
                                ? filter.treatmentStatusIn()
                                : null,

                        filter != null
                                && filter.encounterReasons() != null
                                && !filter.encounterReasons().isEmpty()
                                ? filter.encounterReasons()
                                : null,

                        filter != null && filter.doctorStartedFrom() != null,

                        filter != null && filter.doctorStartedTo() != null,

                        filter != null
                                ? toInstant(filter.doctorStartedFrom())
                                : null,

                        filter != null
                                ? toInstant(filter.doctorStartedTo())
                                : null,

                        pageable
                );

        List<PatientEncounter> encounters = page.getContent();

        if (encounters.isEmpty()) {
            return page.map(this::toEmptyViewModel);
        }

        Set<Long> patientIds = encounters.stream()
                .map(PatientEncounter::getPatient)
                .filter(patient -> patient != null && patient.getId() != null)
                .map(Patient::getId)
                .collect(Collectors.toSet());

        Map<Long, PatientInsurance> insuranceMap = new HashMap<>();

        for (Long patientId : patientIds) {
            try {
                patientInsuranceRepository
                        .findFirstByPatient_Id(patientId)
                        .ifPresent(insurance ->
                                insuranceMap.put(patientId, insurance)
                        );
            } catch (Exception ignored) {
            }
        }

        Map<Long, List<PatientServiceAndProduct>> serviceMap = new HashMap<>();

        for (PatientEncounter encounter : encounters) {

            if (encounter.getId() == null) {
                continue;
            }

            try {
                List<PatientServiceAndProduct> products =
                        patientServiceAndProductRepository
                                .findByEncounterId(encounter.getId());

                serviceMap.put(
                        encounter.getId(),
                        products != null ? products : List.of()
                );

            } catch (Exception ignored) {
                serviceMap.put(
                        encounter.getId(),
                        List.of()
                );
            }
        }


        List<Long> encounterIds = encounters.stream()
                .map(PatientEncounter::getId)
                .filter(id -> id != null)
                .toList();

        Set<Long> observedEncounterIds = new HashSet<>();

        if (!encounterIds.isEmpty()) {

            observedEncounterIds.addAll(
                    additionalMeasurementsRepository
                            .findDistinctByEncounterIdIn(encounterIds)
                            .stream()
                            .map(m -> m.getEncounterId())
                            .filter(id -> id != null)
                            .collect(Collectors.toSet())
            );

            observedEncounterIds.addAll(
                    vitalSignsRepository
                            .findDistinctByEncounterIdIn(encounterIds)
                            .stream()
                            .map(v -> v.getEncounterId())
                            .filter(id -> id != null)
                            .collect(Collectors.toSet())
            );

            observedEncounterIds.addAll(
                    painAssessmentRepository
                            .findDistinctByEncounterIdIn(encounterIds)
                            .stream()
                            .map(p -> p.getEncounterId())
                            .filter(id -> id != null)
                            .collect(Collectors.toSet())
            );

            observedEncounterIds.addAll(
                    bodyMeasurementsRepository
                            .findDistinctByEncounterIdIn(encounterIds)
                            .stream()
                            .map(b -> b.getEncounterId())
                            .filter(id -> id != null)
                            .collect(Collectors.toSet())
            );

            observedEncounterIds.addAll(
                    patientObservationsComplaintsRepository
                            .findDistinctByEncounterIdIn(encounterIds)
                            .stream()
                            .map(o -> o.getEncounterId())
                            .filter(id -> id != null)
                            .collect(Collectors.toSet())
            );
        }


        Map<Long, String> practitionerNames = new HashMap<>();
        Map<Long, String> departmentNames = new HashMap<>();
        Map<Long, String> serviceNames = new HashMap<>();


        Set<Long> practitionerIds = encounters.stream()
                .map(PatientEncounter::getPractitionerId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        for (Long practitionerId : practitionerIds) {

            try {
                PractitionerDTO practitioner =
                        practitionerHelper.getPractitioner(practitionerId);

                if (practitioner != null) {

                    String name = String.join(
                            " ",
                            safe(practitioner.firstName()),
                            safe(practitioner.lastName())
                    ).trim();

                    practitionerNames.put(
                            practitionerId,
                            name.isBlank() ? "-" : name
                    );

                } else {
                    practitionerNames.put(
                            practitionerId,
                            "-"
                    );
                }

            } catch (Exception ignored) {
                practitionerNames.put(
                        practitionerId,
                        "-"
                );
            }
        }

        Set<Long> departmentIds = encounters.stream()
                .map(PatientEncounter::getDepartmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        for (Long departmentId : departmentIds) {

            try {
                var department =
                        departmentHelper.getDepartment(departmentId);

                departmentNames.put(
                        departmentId,
                        department != null
                                ? department.name()
                                : "-"
                );

            } catch (Exception ignored) {
                departmentNames.put(
                        departmentId,
                        "-"
                );
            }
        }

        Set<Long> serviceIds = serviceMap.values()
                .stream()
                .flatMap(List::stream)
                .map(PatientServiceAndProduct::getServiceId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        for (Long serviceId : serviceIds) {

            try {
                ServiceSetupDTO service =
                        serviceHelper.getService(serviceId);

                serviceNames.put(
                        serviceId,
                        service != null
                                ? service.name()
                                : "-"
                );

            } catch (Exception ignored) {
                serviceNames.put(
                        serviceId,
                        "-"
                );
            }
        }

        return page.map(encounter ->
                toViewModel(
                        encounter,
                        insuranceMap,
                        serviceMap,
                        practitionerNames,
                        serviceNames,
                        departmentNames,
                        observedEncounterIds
                )
        );
    }

    private EncounterListVM toViewModel(
            PatientEncounter encounter,
            Map<Long, PatientInsurance> insuranceMap,
            Map<Long, List<PatientServiceAndProduct>> serviceMap,
            Map<Long, String> practitionerNames,
            Map<Long, String> serviceNames,
            Map<Long, String> departmentNames,
            Set<Long> observedEncounterIds
    ) {

        Patient patient = encounter.getPatient();

        Long patientId =
                patient != null
                        ? patient.getId()
                        : null;

        String patientFullName =
                buildPatientName(patient);

        Integer age =
                patient != null && patient.getDateOfBirth() != null
                        ? calculateAge(patient.getDateOfBirth())
                        : null;

        String documentType = null;

        if (patientId != null) {
            try {
                documentType =
                        patientDocumentRepository
                                .findFirstByPatient_IdAndIsPrimaryTrue(patientId)
                                .map(document ->
                                        document.getType() != null
                                                ? document.getType().name()
                                                : null
                                )
                                .orElse(null);
            } catch (Exception ignored) {
                documentType = null;
            }
        }

        List<PatientServiceAndProduct> products =
                serviceMap.getOrDefault(
                        encounter.getId(),
                        List.of()
                );

        PatientServiceAndProduct defaultService =
                products.stream()
                        .filter(product ->
                                Boolean.TRUE.equals(
                                        product.getIsDefaultService()
                                )
                        )
                        .findFirst()
                        .orElse(null);

        if (defaultService == null && !products.isEmpty()) {
            defaultService = products.get(0);
        }

        BigDecimal amount =
                products.stream()
                        .map(PatientServiceAndProduct::getNetAmount)
                        .filter(value -> value != null)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        String paymentStatus =
                resolvePaymentStatus(products);

        String paymentType =
                encounter.getCoverageType() != null
                        ? encounter.getCoverageType().name()
                        : defaultService != null
                        ? defaultService.getPaymentType()
                        : null;

        PatientInsurance insurance =
                patientId != null
                        ? insuranceMap.get(patientId)
                        : null;

        String insuranceName =
                insurance != null
                        ? insurance.getPayerName()
                        : null;

        String coverageType =
                encounter.getCoverageType() != null
                        ? encounter.getCoverageType().name()
                        : insurance != null
                        ? insurance.getCoverageType()
                        : null;

        String defaultServiceName =
                defaultService != null &&
                        defaultService.getServiceId() != null
                        ? serviceNames.get(
                        defaultService.getServiceId()
                )
                        : null;

        Long practitionerId =
                encounter.getPractitionerId();

        String practitionerName =
                practitionerId != null
                        ? practitionerNames.get(practitionerId)
                        : null;

        Long departmentId =
                encounter.getDepartmentId();

        String departmentName =
                departmentId != null
                        ? departmentNames.get(departmentId)
                        : null;

        boolean triageStarted =
                isTriageStarted(encounter.getStatus());

        String encounterStatus =
                encounter.getEncounterStatus() != null
                        ? encounter.getEncounterStatus().name()
                        : null;

        return new EncounterListVM(

                encounter.getId(),

                patientId,
                patientFullName,

                patient != null
                        ? patient.getMedicalRecordNumber()
                        : null,

                age,

                patient != null &&
                        patient.getSexAtBirth() != null
                        ? patient.getSexAtBirth().name()
                        : null,

                documentType,

                patient != null
                        ? patient.getDocumentId()
                        : null,

                patient != null
                        ? patient.getPrimaryMobileNumber()
                        : null,

                encounter.getEncounterNumber(),

                encounter.getEncounterDate(),

                encounter.getEncounterTime() != null
                        ? encounter.getEncounterTime().toString()
                        : null,

                encounter.getEncounterType(),
                encounter.getEncounterReason(),
                encounter.getPriorityLevel(),

                departmentId,
                departmentName,

                practitionerId,
                practitionerName,

                defaultServiceName,

                amount,

                paymentStatus,

                coverageType,

                paymentType,

                insuranceName,

                triageStarted,

                toLocalDateTime(
                        encounter.getStartedDate()
                ),

                encounterStatus,

                encounter.getStatus(),

                observedEncounterIds.contains(
                        encounter.getId()
                )
        );
    }

    private EncounterListVM toEmptyViewModel(
            PatientEncounter encounter
    ) {
        return new EncounterListVM(

                encounter.getId(),

                encounter.getPatient() != null
                        ? encounter.getPatient().getId()
                        : null,

                buildPatientName(encounter.getPatient()),

                encounter.getPatient() != null
                        ? encounter.getPatient().getMedicalRecordNumber()
                        : null,

                encounter.getPatient() != null &&
                        encounter.getPatient().getDateOfBirth() != null
                        ? calculateAge(
                        encounter.getPatient().getDateOfBirth()
                )
                        : null,

                encounter.getPatient() != null &&
                        encounter.getPatient().getSexAtBirth() != null
                        ? encounter.getPatient().getSexAtBirth().name()
                        : null,

                null,

                encounter.getPatient() != null
                        ? encounter.getPatient().getDocumentId()
                        : null,

                encounter.getPatient() != null
                        ? encounter.getPatient().getPrimaryMobileNumber()
                        : null,

                encounter.getEncounterNumber(),

                encounter.getEncounterDate(),

                encounter.getEncounterTime() != null
                        ? encounter.getEncounterTime().toString()
                        : null,

                encounter.getEncounterType(),
                encounter.getEncounterReason(),

                encounter.getPriorityLevel(),

                encounter.getDepartmentId(),
                null,

                encounter.getPractitionerId(),
                null,

                null,

                BigDecimal.ZERO,

                null,

                encounter.getCoverageType() != null
                        ? encounter.getCoverageType().name()
                        : null,

                null,

                null,

                isTriageStarted(encounter.getStatus()),

                toLocalDateTime(
                        encounter.getStartedDate()
                ),

                encounter.getEncounterStatus() != null
                        ? encounter.getEncounterStatus().name()
                        : null,

                encounter.getStatus(),

                false
        );
    }

    private String resolvePaymentStatus(
            List<PatientServiceAndProduct> products
    ) {

        if (products == null || products.isEmpty()) {
            return null;
        }

        boolean allPaid =
                products.stream()
                        .allMatch(product ->
                                product.getPaymentStatus() != null &&
                                        PaymentStatus.PAID.name().equals(
                                                product.getPaymentStatus().name()
                                        )
                        );

        if (allPaid) {
            return PaymentStatus.PAID.name();
        }

        boolean anyRemaining =
                products.stream()
                        .anyMatch(product ->
                                product.getRemainingAmount() != null &&
                                        product.getRemainingAmount()
                                                .compareTo(BigDecimal.ZERO) > 0
                        );

        if (anyRemaining) {
            return "REMAINING_TO_PAY";
        }

        return products.get(0).getPaymentStatus() != null
                ? products.get(0).getPaymentStatus().name()
                : null;
    }

    private boolean isTriageStarted(
            com.dazzle.asklepios.domain.enumeration.TreatmentStatus status
    ) {

        if (status == null) {
            return false;
        }

        return switch (status) {
            case TRIAGE_STARTED,
                 ASSIGNED_TO_BED,
                 ONGOING,
                 IN_OPERATION,
                 CONFIRM_RETURN,
                 TEMP_DC,
                 COMPLETED,
                 CLOSED,
                 DISCHARGED -> true;

            default -> false;
        };
    }

    private Integer calculateAge(
            LocalDate dateOfBirth
    ) {
        return Period.between(
                dateOfBirth,
                LocalDate.now()
        ).getYears();
    }

    private String buildPatientName(
            Patient patient
    ) {

        if (patient == null) {
            return "-";
        }

        String fullName =
                String.join(
                        " ",
                        safe(patient.getFirstName()),
                        safe(patient.getSecondName()),
                        safe(patient.getThirdName()),
                        safe(patient.getLastName())
                ).trim();

        return fullName.isBlank()
                ? "-"
                : fullName;
    }

    private String safe(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    private Instant toInstant(
            java.time.LocalDateTime value
    ) {
        if (value == null) {
            return null;
        }

        return value
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    private java.time.LocalDateTime toLocalDateTime(
            Instant value
    ) {
        if (value == null) {
            return null;
        }

        return java.time.LocalDateTime.ofInstant(
                value,
                ZoneId.systemDefault()
        );
    }

    private List<EncounterStatus> extractEncounterStatuses(
            EncounterListFilterDTO filter
    ) {

        if (filter == null ||
                filter.encounterStatusIn() == null ||
                filter.encounterStatusIn().isEmpty()) {
            return null;
        }

        return filter.encounterStatusIn()
                .stream()
                .filter(value ->
                        value != null &&
                                !value.isBlank()
                )
                .map(this::parseEncounterStatus)
                .filter(value -> value != null)
                .toList();
    }

    private EncounterStatus parseEncounterStatus(
            String value
    ) {

        try {
            return EncounterStatus.valueOf(
                    value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}