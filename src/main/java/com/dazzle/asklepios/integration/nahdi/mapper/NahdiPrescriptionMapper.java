package com.dazzle.asklepios.integration.nahdi.mapper;

import com.dazzle.asklepios.client.setup.ActiveIngredientClient;
import com.dazzle.asklepios.client.setup.AllergenClient;
import com.dazzle.asklepios.client.setup.BrandMedicationClient;
import com.dazzle.asklepios.client.setup.PharmaceuticalClient;
import com.dazzle.asklepios.client.setup.ICDTreeClient;
import com.dazzle.asklepios.client.setup.MedicationCategoryClassClient;
import com.dazzle.asklepios.client.setup.PractitionerClient;
import com.dazzle.asklepios.client.setup.PrescriptionInstructionClient;
import com.dazzle.asklepios.client.setup.dto.ActiveIngredientDTO;
import com.dazzle.asklepios.client.setup.dto.BrandMedicationSetupDTO;
import com.dazzle.asklepios.client.setup.dto.PharmaceuticalDTO;
import com.dazzle.asklepios.client.setup.dto.ICDDiagnosisDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.client.setup.dto.PrescriptionInstructionDTO;
import com.dazzle.asklepios.client.setup.dto.SetupNameDTO;
import com.dazzle.asklepios.domain.ApLovValue;
import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientAllergies;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.AllergenTypes;
import com.dazzle.asklepios.domain.enumeration.PatientAllergyStatus;
import com.dazzle.asklepios.domain.enumeration.PatientCondition;
import com.dazzle.asklepios.domain.enumeration.PrescriptionInstructionsType;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.nahdi.config.NahdiProperties;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiPrescriptionDiagnosis;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiPrescriptionHead;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiPrescriptionItem;
import com.dazzle.asklepios.integration.nahdi.dto.prescription.NahdiSendPrescriptionRequest;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.PatientAllergiesRepository;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the Nahdi SendPrescription request from a submitted HIS prescription.
 */
@Component
@RequiredArgsConstructor
public class NahdiPrescriptionMapper {

    private static final Logger LOG = LoggerFactory.getLogger(NahdiPrescriptionMapper.class);

    static final String DIAGNOSIS_TYPE_PRINCIPLE = "principle";
    static final String ITEM_CODE_TYPE_SFDA = "SFDA";
    static final String ITEM_CODE_TYPE_NPHIES = "NPHIES";
    static final String NO_ALLERGY = "N/A";
    static final String UNKNOWN_ALLERGY = "Unknown";
    static final String YES = "Y";
    static final String NO = "N";

    private static final ZoneId NAHDI_ZONE = ZoneId.of("Asia/Riyadh");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final NahdiProperties nahdiProperties;
    private final PatientPrescriptionRepository patientPrescriptionRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientDocumentRepository patientDocumentRepository;
    private final PatientAllergiesRepository patientAllergiesRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final ApLovValueRepository apLovValueRepository;
    private final ChiefComplainRepository chiefComplainRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PractitionerClient practitionerClient;
    private final PrescriptionInstructionClient prescriptionInstructionClient;
    private final ICDTreeClient icdTreeClient;
    private final BrandMedicationClient brandMedicationClient;
    private final PharmaceuticalClient pharmaceuticalClient;
    private final ActiveIngredientClient activeIngredientClient;
    private final AllergenClient allergenClient;
    private final MedicationCategoryClassClient medicationCategoryClassClient;

    @Transactional(readOnly = true)
    public NahdiSendPrescriptionRequest toSendPrescriptionRequest(Long prescriptionId) {
        PatientPrescription prescription = patientPrescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + prescriptionId));
        PatientEncounter encounter = prescription.getEncounterId() == null
                ? null
                : patientEncounterRepository.findById(prescription.getEncounterId()).orElse(null);

        List<PatientPrescriptionMedication> medications = activeMedications(prescription);

        return NahdiSendPrescriptionRequest.builder()
                .presHead(toHead(prescription, encounter))
                .presDiagnosis(toDiagnoses(medications))
                .presItem(toItems(medications))
                .build();
    }

    private NahdiPrescriptionHead toHead(PatientPrescription prescription, PatientEncounter encounter) {
        Patient patient = prescription.getPatient();
        PractitionerDTO practitioner = encounter == null || encounter.getPractitionerId() == null
                ? null
                : fetch("practitioner " + encounter.getPractitionerId(), () -> practitionerClient.getPractitioner(encounter.getPractitionerId()));
        boolean insuranceVisit = encounter != null && BillingCoverageType.INSURANCE.equals(encounter.getCoverageType());
        // Insurance data is sent only for insurance visits.
        PatientInsurance insurance = !insuranceVisit || encounter.getPatientInsuranceId() == null
                ? null
                : patientInsuranceRepository.findById(encounter.getPatientInsuranceId()).orElse(null);
        VitalSigns vitals = encounter == null
                ? null
                : vitalSignsRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounter.getId()).orElse(null);
        PatientObservationsComplaints observations = encounter == null
                ? null
                : patientObservationsComplaintsRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounter.getId()).orElse(null);
        String presDateTime = formatDateTime(Optional.ofNullable(prescription.getSubmitedDate()).orElseGet(Instant::now));

        return NahdiPrescriptionHead.builder()
                .providerId(text(nahdiProperties.providerId()))
                .providerToken(text(nahdiProperties.providerToken()))
                .presRefId(firstNonBlank(prescription.getPrescriptionNum(), String.valueOf(prescription.getId())))
                .presDateTime(presDateTime)
                .presDateTime2(presDateTime)
                .presStatus(nahdiProperties.prescription().status())
                .presType(insuranceVisit ? nahdiProperties.prescription().typeInsurance() : nahdiProperties.prescription().typeCash())
                .presDocCode(practitioner == null ? "" : text(practitioner.defaultMedicalLicense()))
                .presDocName(practitioner == null ? "" : join(" ", practitioner.firstName(), practitioner.lastName()))
                .presDocSpe(practitioner == null ? "" : lovDisplay(practitioner.specialty()))
                .presNote("")
                .patientFileNo(patient == null ? "" : text(patient.getMedicalRecordNumber()))
                .patientNameEn(patient == null ? "" : join(" ", patient.getFirstName(), patient.getSecondName(), patient.getThirdName(), patient.getLastName()))
                // Second-language (Arabic) name; the primary name when it is not recorded.
                .patientNameAr(patient == null ? "" : firstNonBlank(
                        join(" ", patient.getFirstNameSecondaryLang(), patient.getSecondNameSecondaryLang(),
                                patient.getThirdNameSecondaryLang(), patient.getLastNameSecondaryLang()),
                        join(" ", patient.getFirstName(), patient.getSecondName(), patient.getThirdName(), patient.getLastName())))
                .patientGender(patient == null ? "" : toGender(patient.getSexAtBirth()))
                .patientDob(patient == null || patient.getDateOfBirth() == null ? "" : patient.getDateOfBirth().format(DATE_FORMAT))
                .patientAge(patient == null ? "" : toAge(patient.getDateOfBirth()))
                .patientAllergy(patient == null ? NO_ALLERGY : toAllergies(patient.getId()))
                .patientMobileNo(patient == null ? "" : text(patient.getPrimaryMobileNumber()))
                .patientNationalId(patient == null ? "" : toDocumentNumber(patient))
                .patientInsExpDate(insurance == null || insurance.getExpirationDate() == null ? "" : insurance.getExpirationDate().format(DATE_FORMAT))
                .patientInsCardNo(insurance == null ? "" : text(insurance.getMemberCardId()))
                .patientInsPolicyNo(insurance == null ? "" : text(insurance.getPolicyNumber()))
                .patientInsClass(insurance == null ? "" : text(insurance.getPolicyClassName()))
                .patientInsPolicyName(insurance == null ? "" : text(insurance.getPolicyHolderName()))
                .patientInsCompany(insurance == null ? "" : text(insurance.getPayerName()))
                .patientInsVisitPaid(null)
                .patientInsMaxCoPay(insurance == null ? null : insurance.getDefaultMaximumCopayment())
                .patientInsCoPayPercent(insurance == null ? "" : text(insurance.getDefaultCopaymentPercent()))
                .patientInsCoPayDiscountPercent("")
                .brandCoPayPercent("")
                .brandCoPayMax("")
                .genericCoPayPercent("")
                .genericCoPayMax("")
                .insuranceDiscount("")
                .weight(patient == null ? "" : text(latestWeight(patient.getId(), encounter)))
                .height(patient == null ? "" : text(latestHeight(patient.getId(), encounter)))
                .temperature(vitals == null ? "" : text(vitals.getTemperature()))
                .bpHigh(vitals == null ? "" : text(vitals.getBloodPressureSystolic()))
                .bpLow(vitals == null ? "" : text(vitals.getBloodPressureDiastolic()))
                .pulseRate(vitals == null ? "" : text(vitals.getHeartRate()))
                .pregnant(femaleFlag(patient, isPregnant(encounter, observations)))
                .lactating(femaleFlag(patient, observations != null && Boolean.TRUE.equals(observations.getIsLactating())))
                .build();
    }

    private static List<PatientPrescriptionMedication> activeMedications(PatientPrescription prescription) {
        return prescription.getMedications() == null
                ? List.of()
                : prescription.getMedications().stream()
                        .filter(Objects::nonNull)
                        .filter(medication -> !PrescriptionStatus.CANCELLED.equals(medication.getStatus()))
                        .toList();
    }

    /**
     * Diagnoses are the indication ICDs of the prescribed items (each ICD sent once), all as "principle".
     */
    private List<NahdiPrescriptionDiagnosis> toDiagnoses(List<PatientPrescriptionMedication> medications) {
        List<NahdiPrescriptionDiagnosis> diagnoses = new ArrayList<>();
        medications.stream()
                .map(PatientPrescriptionMedication::getIndicationIcd)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(icdId -> {
                    ICDDiagnosisDTO icd = fetch("ICD diagnosis " + icdId, () -> icdTreeClient.getDiagnosisById(icdId));
                    if (icd != null) {
                        diagnoses.add(new NahdiPrescriptionDiagnosis(
                                DIAGNOSIS_TYPE_PRINCIPLE,
                                text(icd.icdCode()),
                                firstNonBlank(icd.icdShortDescription(), icd.icdFullDescription())
                        ));
                    }
                });
        return diagnoses;
    }

    private List<NahdiPrescriptionItem> toItems(List<PatientPrescriptionMedication> medications) {
        List<NahdiPrescriptionItem> items = new ArrayList<>();
        int lineNo = 1;
        for (PatientPrescriptionMedication medication : medications) {
            items.add(toItem(medication, lineNo++));
        }
        return items;
    }

    private NahdiPrescriptionItem toItem(PatientPrescriptionMedication medication, int lineNo) {
        PharmaceuticalDTO pharmaceutical = medication.getPharmaceuticalId() == null
                ? null
                : fetch("pharmaceutical " + medication.getPharmaceuticalId(), () -> pharmaceuticalClient.getPharmaceutical(medication.getPharmaceuticalId()));
        // Brand is kept only as a fallback for prescriptions recorded before pharmaceuticals.
        BrandMedicationSetupDTO brand = pharmaceutical != null || medication.getMedicationsId() == null
                ? null
                : fetch("brand medication " + medication.getMedicationsId(), () -> brandMedicationClient.getBrandMedication(medication.getMedicationsId()));
        ActiveIngredientDTO activeIngredient = pharmaceutical != null || brand != null || medication.getActiveIngredientId() == null
                ? null
                : fetch("active ingredient " + medication.getActiveIngredientId(), () -> activeIngredientClient.getActiveIngredient(medication.getActiveIngredientId()));
        PatientServiceAndProduct serviceAndProduct = patientServiceAndProductRepository
                .findByServiceSourceAndSourceIdAndBillingItemType(ServiceSource.PRESCRIPTION, medication.getId(), BillingItemTypes.MEDICATION)
                .orElse(null);

        BigDecimal price = serviceAndProduct != null && serviceAndProduct.getUnitPrice() != null && serviceAndProduct.getUnitPrice().signum() > 0
                ? serviceAndProduct.getUnitPrice()
                : brand == null ? null : brand.price();

        ItemCode itemCode = toItemCode(pharmaceutical, brand);

        return NahdiPrescriptionItem.builder()
                .lineNo(String.valueOf(lineNo))
                .codeType(itemCode.type())
                .code(itemCode.code())
                .description(firstNonBlank(
                        pharmaceutical == null ? null : pharmaceutical.pharmaceuticalName(),
                        brand == null ? null : brand.name(),
                        activeIngredient == null ? null : activeIngredient.name(),
                        medication.getOtherMedicationName()))
                .qty(serviceAndProduct == null || serviceAndProduct.getQuantity() == null ? null : BigDecimal.valueOf(serviceAndProduct.getQuantity()))
                .dose(toDoseText(medication))
                .price(text(price))
                .appStatus(nahdiProperties.prescription().itemApprovalStatus())
                .build();
    }

    private record ItemCode(String type, String code) {}

    /**
     * The code type set by {@code nahdi.prescription.item-code-priority} (SFDA by default) is sent first;
     * when the pharmaceutical has no such code, the other one (SFDA or NPHIES) is used.
     */
    private ItemCode toItemCode(PharmaceuticalDTO pharmaceutical, BrandMedicationSetupDTO brand) {
        if (pharmaceutical == null) {
            // Prescriptions recorded before pharmaceuticals only have the brand code.
            return new ItemCode(ITEM_CODE_TYPE_SFDA, brand == null ? "" : text(brand.code()));
        }

        ItemCode sfda = new ItemCode(ITEM_CODE_TYPE_SFDA, text(pharmaceutical.code()));
        ItemCode nphies = new ItemCode(ITEM_CODE_TYPE_NPHIES, text(pharmaceutical.billingCode()));
        boolean nphiesFirst = ITEM_CODE_TYPE_NPHIES.equalsIgnoreCase(text(nahdiProperties.prescription().itemCodePriority()));

        ItemCode preferred = nphiesFirst ? nphies : sfda;
        ItemCode fallback = nphiesFirst ? sfda : nphies;
        return !preferred.code().isEmpty() || fallback.code().isEmpty() ? preferred : fallback;
    }

    /**
     * Readable dosing text, e.g. "1 Tablet Oral Every 8 Hours for 7 Days".
     */
    private String toDoseText(PatientPrescriptionMedication medication) {
        String duration = medication.getDuration() == null
                ? null
                : join(" ", "for", text(medication.getDuration()), lovDisplay(medication.getDurationType()));
        return join(" ", toInstructionText(medication), duration);
    }

    private String toInstructionText(PatientPrescriptionMedication medication) {
        PrescriptionInstructionsType type = medication.getInstructionsType();

        // Predefined instructions store the id of the setup instruction, not its text.
        if (PrescriptionInstructionsType.PRE_DEFINED_INSTRUCTIONS.equals(type)) {
            Long instructionId = toLong(medication.getInstructions());
            PrescriptionInstructionDTO instruction = instructionId == null
                    ? null
                    : fetch("prescription instruction " + instructionId, () -> prescriptionInstructionClient.getPrescriptionInstruction(instructionId));
            return instruction == null
                    ? ""
                    : join(" ", text(instruction.dose()), lovDisplay(instruction.unit()), lovDisplay(instruction.rout()), lovDisplay(instruction.frequency()));
        }

        if (PrescriptionInstructionsType.MANUAL_INSTRUCTIONS.equals(type)) {
            return text(medication.getInstructions());
        }

        return join(" ",
                text(medication.getDose()),
                lovDisplay(medication.getDoesUnit()),
                lovDisplay(medication.getRout()),
                lovDisplay(medication.getFrequency()));
    }

    /**
     * "EVERY_8_HOURS" -> "Every 8 Hours".
     */
    /**
     * LOV keys (e.g. duration type "2412573392756351") are sent as their display value;
     * enum names (e.g. "EVERY_8_HOURS") are made readable.
     */
    private String lovDisplay(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return apLovValueRepository.findById(value.trim())
                .map(ApLovValue::getLovDisplayVale)
                .filter(display -> display != null && !display.isBlank())
                .map(String::trim)
                .orElseGet(() -> readable(value));
    }

    private static String readable(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Stream.of(value.trim().split("_"))
                .filter(word -> !word.isBlank())
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private static Long toLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toAllergies(Long patientId) {
        String allergies = patientAllergiesRepository.findByPatientId(patientId, Pageable.unpaged())
                .stream()
                .filter(allergy -> PatientAllergyStatus.ACTIVE.equals(allergy.getStatus()))
                .map(this::toAllergyName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return allergies.isEmpty() ? NO_ALLERGY : allergies;
    }

    /**
     * Only OTHER allergies store a free-text name; setup allergens and medication classes store an id.
     */
    private String toAllergyName(PatientAllergies allergy) {
        if (allergy.getAllergenName() != null && !allergy.getAllergenName().isBlank()) {
            return allergy.getAllergenName().trim();
        }
        if (AllergenTypes.MEDICATION.equals(allergy.getAllergenType()) && allergy.getMedicationClassId() != null) {
            SetupNameDTO medicationClass = fetch("medication class " + allergy.getMedicationClassId(),
                    () -> medicationCategoryClassClient.getMedicationCategoryClass(allergy.getMedicationClassId()));
            return medicationClass == null ? "" : text(medicationClass.name());
        }
        if (allergy.getAllergenId() != null) {
            SetupNameDTO allergen = fetch("allergen " + allergy.getAllergenId(), () -> allergenClient.getAllergen(allergy.getAllergenId()));
            return allergen == null ? "" : text(allergen.name());
        }
        return AllergenTypes.UNKNOWN.equals(allergy.getAllergenType()) ? UNKNOWN_ALLERGY : "";
    }

    /**
     * Weight recorded in this encounter, otherwise the patient's latest recorded weight.
     */
    private BigDecimal latestWeight(Long patientId, PatientEncounter encounter) {
        return Optional.ofNullable(encounter)
                .flatMap(e -> bodyMeasurementsRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(e.getId()))
                .map(BodyMeasurements::getWeight)
                .or(() -> bodyMeasurementsRepository.findFirstByPatientIdAndWeightIsNotNullOrderByCreatedDateDesc(patientId)
                        .map(BodyMeasurements::getWeight))
                .orElse(null);
    }

    /**
     * Height recorded in this encounter, otherwise the patient's latest recorded height.
     */
    private BigDecimal latestHeight(Long patientId, PatientEncounter encounter) {
        return Optional.ofNullable(encounter)
                .flatMap(e -> bodyMeasurementsRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(e.getId()))
                .map(BodyMeasurements::getHeight)
                .or(() -> bodyMeasurementsRepository.findFirstByPatientIdAndHeightIsNotNullOrderByCreatedDateDesc(patientId)
                        .map(BodyMeasurements::getHeight))
                .orElse(null);
    }

    /**
     * Pregnant when ticked in the encounter's patient observations, or recorded as PREGNANCY on its chief complaint.
     */
    private boolean isPregnant(PatientEncounter encounter, PatientObservationsComplaints observations) {
        if (observations != null && Boolean.TRUE.equals(observations.getIsPregnant())) {
            return true;
        }
        return encounter != null && chiefComplainRepository.findTopByEncounter_IdOrderByCreatedDateDesc(encounter.getId())
                .map(chiefComplain -> PatientCondition.PREGNANCY.equals(chiefComplain.getPatientCondition()))
                .orElse(false);
    }

    /**
     * Y/N for female patients; empty otherwise (not applicable).
     */
    private static String femaleFlag(Patient patient, boolean value) {
        if (patient == null || !Gender.FEMALE.equals(patient.getSexAtBirth())) {
            return "";
        }
        return value ? YES : NO;
    }

    /**
     * Patient identity number of any document type (national ID, iqama, passport, ...):
     * patient.documentId, otherwise the primary (then oldest) recorded document.
     */
    private String toDocumentNumber(Patient patient) {
        if (patient.getDocumentId() != null && !patient.getDocumentId().isBlank()) {
            return patient.getDocumentId().trim();
        }
        return patientDocumentRepository.findByPatient_IdOrderByIsPrimaryDescIdAsc(patient.getId())
                .stream()
                .filter(document -> !DocumentType.NO_DOCUMENT.equals(document.getType()))
                .map(PatientDocument::getNumber)
                .filter(number -> number != null && !number.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse("");
    }

    private static String toGender(Gender gender) {
        if (gender == null) {
            return "";
        }
        return gender == Gender.MALE ? "M" : "F";
    }

    private static String toAge(LocalDate dateOfBirth) {
        return dateOfBirth == null ? "" : String.valueOf(Period.between(dateOfBirth, LocalDate.now(NAHDI_ZONE)).getYears());
    }

    private static String formatDateTime(Instant instant) {
        return DATE_TIME_FORMAT.format(instant.atZone(NAHDI_ZONE));
    }

    private static <T> T fetch(String description, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            LOG.warn("[NAHDI][MAPPER] failed to load {}: {}", description, e.getMessage());
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return value.toString().trim();
    }

    private static String join(String separator, String... parts) {
        return Stream.of(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(separator));
    }

    private static String firstNonBlank(String... values) {
        return Stream.of(values)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse("");
    }
}
