package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.service.PatientService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.patient.PatientCreateVM;
import com.dazzle.asklepios.web.rest.vm.patient.PatientResponseVM;
import com.dazzle.asklepios.web.rest.vm.patient.PatientUpdateVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class PatientController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientController.class);

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }


    @PostMapping("/patients")
    public ResponseEntity<PatientResponseVM> createPatient(
            @Valid @RequestBody PatientCreateVM vm
    ) {
        LOG.debug("REST create Patient payload={}", vm);
         System.out.println("++++++++++++++++>"+vm.dateOfBirth());
        Patient toCreate = Patient.builder()
                .firstName(vm.firstName())
                .secondName(vm.secondName())
                .thirdName(vm.thirdName())
                .lastName(vm.lastName())
                .sexAtBirth(vm.sexAtBirth())
                .dateOfBirth(vm.dateOfBirth())
                .patientClasses(vm.patientClasses())
                .isPrivatePatient(vm.isPrivatePatient())

                .firstNameSecondaryLang(vm.firstNameSecondaryLang())
                .secondNameSecondaryLang(vm.secondNameSecondaryLang())
                .thirdNameSecondaryLang(vm.thirdNameSecondaryLang())
                .lastNameSecondaryLang(vm.lastNameSecondaryLang())

                .primaryMobileNumber(vm.primaryMobileNumber())
                .secondMobileNumber(vm.secondMobileNumber())
                .homePhone(vm.homePhone())
                .workPhone(vm.workPhone())
                .email(vm.email())
                .receiveSms(vm.receiveSms())
                .receiveEmail(vm.receiveEmail())
                .preferredWayOfContact(vm.preferredWayOfContact())

                .nativeLanguage(vm.nativeLanguage())
                .emergencyContactName(vm.emergencyContactName())
                .emergencyContactRelation(vm.emergencyContactRelation())
                .emergencyContactPhone(vm.emergencyContactPhone())

                .role(vm.role())
                .maritalStatus(vm.maritalStatus())
                .nationality(vm.nationality())
                .religion(vm.religion())
                .ethnicity(vm.ethnicity())
                .occupation(vm.occupation())
                .responsibleParty(vm.responsibleParty())
                .educationalLevel(vm.educationalLevel())

                .previousId(vm.previousId())
                .archivingNumber(vm.archivingNumber())

                .details(vm.details())
                .isUnknown(vm.isUnknown())
                .isVerified(vm.isVerified())
                .isCompletedPatient(vm.isCompletedPatient())
                .build();

        Patient created = patientService.create(toCreate);
        PatientResponseVM body = PatientResponseVM.ofEntity(created);

        return ResponseEntity
                .created(URI.create("/api/patients/" + created.getId()))
                .body(body);
    }


    @PostMapping("/patients/unknown")
    public ResponseEntity<Patient> createUnknownPatient() {
        LOG.debug("REST request to create unknown patient");

        Patient unknownPatient = Patient.builder()
                .isUnknown(true)
                .build();

        Patient created = patientService.create(unknownPatient);

        return ResponseEntity
                .created(URI.create("/api/patient/patients/raw/" + created.getId()))
                .body(created);
    }



    @PutMapping("/patients/{id}")
    public ResponseEntity<PatientResponseVM> updatePatient(
            @PathVariable Long id,
            @Valid @RequestBody PatientUpdateVM vm
    ) {
        LOG.debug("REST update Patient id={} payload={}", id, vm);

        Patient patch = new Patient();
        patch.setFirstName(vm.firstName());
        patch.setSecondName(vm.secondName());
        patch.setThirdName(vm.thirdName());
        patch.setLastName(vm.lastName());
        patch.setSexAtBirth(vm.sexAtBirth());
        patch.setDateOfBirth(vm.dateOfBirth());
        patch.setPatientClasses(vm.patientClasses());
        patch.setIsPrivatePatient(vm.isPrivatePatient());

        patch.setFirstNameSecondaryLang(vm.firstNameSecondaryLang());
        patch.setSecondNameSecondaryLang(vm.secondNameSecondaryLang());
        patch.setThirdNameSecondaryLang(vm.thirdNameSecondaryLang());
        patch.setLastNameSecondaryLang(vm.lastNameSecondaryLang());

        patch.setPrimaryMobileNumber(vm.primaryMobileNumber());
        patch.setSecondMobileNumber(vm.secondMobileNumber());
        patch.setHomePhone(vm.homePhone());
        patch.setWorkPhone(vm.workPhone());
        patch.setEmail(vm.email());
        patch.setReceiveSms(vm.receiveSms());
        patch.setReceiveEmail(vm.receiveEmail());
        patch.setPreferredWayOfContact(vm.preferredWayOfContact());

        patch.setNativeLanguage(vm.nativeLanguage());
        patch.setEmergencyContactName(vm.emergencyContactName());
        patch.setEmergencyContactRelation(vm.emergencyContactRelation());
        patch.setEmergencyContactPhone(vm.emergencyContactPhone());

        patch.setRole(vm.role());
        patch.setMaritalStatus(vm.maritalStatus());
        patch.setNationality(vm.nationality());
        patch.setReligion(vm.religion());
        patch.setEthnicity(vm.ethnicity());
        patch.setOccupation(vm.occupation());
        patch.setResponsibleParty(vm.responsibleParty());
        patch.setEducationalLevel(vm.educationalLevel());

        patch.setPreviousId(vm.previousId());
        patch.setArchivingNumber(vm.archivingNumber());

        patch.setDetails(vm.details());
        patch.setIsUnknown(vm.isUnknown());
        patch.setIsVerified(vm.isVerified());
        patch.setIsCompletedPatient(vm.isCompletedPatient());

        return patientService.update(id, patch)
                .map(PatientResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @GetMapping("/patients")
    public ResponseEntity<List<PatientResponseVM>> getAllPatients(
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patients pageable={}", pageable);
        final Page<Patient> page = patientService.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return new ResponseEntity<>(
                page.getContent().stream().map(PatientResponseVM::ofEntity).toList(),
                headers,
                HttpStatus.OK
        );
    }


    @GetMapping("/patients/by-mrn/{mrn}")
    public ResponseEntity<List<PatientResponseVM>> getByMrn(
            @PathVariable String mrn,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patients by MRN='{}' pageable={}", mrn, pageable);
        Page<Patient> page = patientService.findByMrn(mrn, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return new ResponseEntity<>(
                page.getContent().stream().map(PatientResponseVM::ofEntity).toList(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/patients/by-archiving-number/{archivingNumber}")
    public ResponseEntity<List<PatientResponseVM>> getByArchivingNumber(
            @PathVariable String archivingNumber,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patients by archivingNumber='{}' pageable={}", archivingNumber, pageable);
        Page<Patient> page = patientService.findByArchivingNumber(archivingNumber, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return new ResponseEntity<>(
                page.getContent().stream().map(PatientResponseVM::ofEntity).toList(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/patients/by-primary-phone/{phone}")
    public ResponseEntity<List<PatientResponseVM>> getByPrimaryPhone(
            @PathVariable("phone") String phone,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patients by primary phone='{}' pageable={}", phone, pageable);
        Page<Patient> page = patientService.findByPrimaryPhone(phone, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return new ResponseEntity<>(
                page.getContent().stream().map(PatientResponseVM::ofEntity).toList(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/patients/by-date-of-birth/{date}")
    public ResponseEntity<List<PatientResponseVM>> getByDateOfBirth(
            @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patients by dateOfBirth='{}' pageable={}", dateOfBirth, pageable);
        Page<Patient> page = patientService.findByDateOfBirth(dateOfBirth, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return new ResponseEntity<>(
                page.getContent().stream().map(PatientResponseVM::ofEntity).toList(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/patients/by-full-name/{keyword}")
    public ResponseEntity<List<PatientResponseVM>> getByFullName(
            @PathVariable("keyword") String keyword,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST list Patients by full name keyword='{}' pageable={}", keyword, pageable);
        Page<Patient> page = patientService.findByFullName(keyword, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );
        return new ResponseEntity<>(
                page.getContent().stream().map(PatientResponseVM::ofEntity).toList(),
                headers,
                HttpStatus.OK
        );
    }
    @GetMapping("/patients/by-document-number")
    public ResponseEntity<List<PatientResponseVM>> getPatientsByPrimaryDocumentNumber(
            @RequestParam("number") String numberPart,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST search Patients by primary document number={} pageable={}", numberPart, pageable);

        Page<Patient> page = patientService.findByPrimaryDocumentNumber(numberPart, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        List<PatientResponseVM> body = page.getContent().stream()
                .map(PatientResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/patients/by-any-document-number")
    public ResponseEntity<List<PatientResponseVM>> getPatientsByAnyDocumentNumber(
            @RequestParam("number") String numberPart,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST search Patients by ANY document number={} pageable={}", numberPart, pageable);

        Page<Patient> page = patientService.findByAnyDocumentNumber(numberPart, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        List<PatientResponseVM> body = page.getContent().stream()
                .map(PatientResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/patients/unknown")
    public Page<Patient> getUnknownPatients(Pageable pageable) {
        return patientService.findUnknownPatients(pageable);
    }




}
