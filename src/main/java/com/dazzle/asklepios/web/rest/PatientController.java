package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.service.PatientService;
import com.dazzle.asklepios.service.dto.patient.PatientCreateDTO;
import com.dazzle.asklepios.service.dto.patient.PatientDuplicationLookupDTO;
import com.dazzle.asklepios.service.dto.patient.PatientUpdateDTO;
import com.dazzle.asklepios.service.dto.patient.UnknownPatientCreateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patient.PatientBasicInformationResponseVM;
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

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientController.class);

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }


    @PostMapping
    public ResponseEntity<Patient> createPatient(
            @Valid @RequestBody PatientCreateDTO patientDTO
    ) {
        LOG.debug("REST create Patient payload={}", patientDTO);

        if (patientDTO == null) {
            throw new BadRequestAlertException(
                    "Patient payload is required",
                    "patient",
                    "payload.required"
            );
        }

        Patient created = patientService.create(patientDTO);

        return ResponseEntity
                .created(URI.create("/api/patient/" + created.getId()))
                .body(created);
    }


    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatient(
            @PathVariable Long id,
            @Valid @RequestBody PatientUpdateDTO patientDTO
    ) {
        LOG.debug("REST update Patient id={} payload={}", id, patientDTO);

        if (patientDTO == null) {
            throw new BadRequestAlertException(
                    "Patient payload is required",
                    "patient",
                    "payload.required"
            );
        }

        if (patientDTO.id() == null || !patientDTO.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "patient",
                    "id.mismatch"
            );
        }

        Patient updatedPatient = patientService.update(id, patientDTO);

        return ResponseEntity.ok(updatedPatient);
    }

    @PostMapping("/unknown")
    public ResponseEntity<Patient> createUnknownPatient() {
        LOG.debug("REST create UNKNOWN Patient (default)");

        UnknownPatientCreateDTO dto =
                UnknownPatientCreateDTO.defaultUnknown();

        Patient created = patientService.createUnknown(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/" + created.getId()))
                .body(created);
    }

    @GetMapping("/by-medicalRecordNumber/{medicalRecordNumber}")
    public ResponseEntity<List<Patient>> getByMedicalRecordNumber(
            @PathVariable String medicalRecordNumber,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list Patients by medicalRecordNumber='{}' pageable={}",
                medicalRecordNumber,
                pageable
        );

        Page<Patient> page =
                patientService.findByMedicalRecordNumber(
                        medicalRecordNumber,
                        pageable
                );

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/by-archiving-number/{archivingNumber}")
    public ResponseEntity<List<Patient>> getByArchivingNumber(
            @PathVariable String archivingNumber,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list Patients by archivingNumber='{}' pageable={}",
                archivingNumber,
                pageable
        );

        Page<Patient> page =
                patientService.findByArchivingNumber(
                        archivingNumber,
                        pageable
                );

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/by-primary-phone/{phone}")
    public ResponseEntity<List<Patient>> getByPrimaryPhone(
            @PathVariable("phone") String phone,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list Patients by primary phone='{}' pageable={}",
                phone,
                pageable
        );

        Page<Patient> page =
                patientService.findByPrimaryPhone(phone, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }


    @GetMapping("/by-date-of-birth/{date}")
    public ResponseEntity<List<Patient>> getByDateOfBirth(
            @PathVariable("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateOfBirth,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list Patients by dateOfBirth='{}' pageable={}",
                dateOfBirth,
                pageable
        );

        Page<Patient> page =
                patientService.findByDateOfBirth(dateOfBirth, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/by-full-name/{keyword}")
    public ResponseEntity<List<Patient>> getByFullName(
            @PathVariable("keyword") String keyword,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST list Patients by full name keyword='{}' pageable={}",
                keyword,
                pageable
        );

        Page<Patient> page =
                patientService.findByFullName(keyword, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/by-document-number")
    public ResponseEntity<List<Patient>> getPatientsByPrimaryDocumentNumber(
            @RequestParam("number") String numberPart,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST search Patients by primary document number={} pageable={}",
                numberPart,
                pageable
        );

        if (numberPart == null || numberPart.isBlank()) {
            throw new BadRequestAlertException(
                    "Document number fragment is required",
                    "patient",
                    "number.required"
            );
        }

        Page<Patient> page =
                patientService.findByPrimaryDocumentNumber(
                        numberPart,
                        pageable
                );

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/by-any-document-number")
    public ResponseEntity<List<Patient>> getPatientsByAnyDocumentNumber(
            @RequestParam("number") String numberPart,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug(
                "REST search Patients by ANY document number={} pageable={}",
                numberPart,
                pageable
        );

        if (numberPart == null || numberPart.isBlank()) {
            throw new BadRequestAlertException(
                    "Document number fragment is required",
                    "patient",
                    "number.required"
            );
        }

        Page<Patient> page =
                patientService.findByAnyDocumentNumber(
                        numberPart,
                        pageable
                );

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }

    @GetMapping("/unknown")
    public ResponseEntity<List<Patient>> getUnknownPatients(
            @ParameterObject Pageable pageable
    ) {
        Page<Patient> page =
                patientService.findUnknownPatients(pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        return new ResponseEntity<>(
                page.getContent(),
                headers,
                HttpStatus.OK
        );
    }
  
    @PostMapping("/bulk/basic-info")
    public ResponseEntity<List<PatientBasicInformationResponseVM>> getBulkPatientBasicInfo(
            @RequestBody List<Long> ids
    ) {
        LOG.debug(
                "REST bulk Patient BASIC INFO idsCount={} ids={}",
                ids == null ? 0 : ids.size(),
                ids
        );

        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<PatientBasicInformationResponseVM> body =
                patientService.findByIds(ids).stream()
                        .map(PatientBasicInformationResponseVM::ofEntity)
                        .toList();

        LOG.debug(
                "REST bulk Patient BASIC INFO responseCount={}",
                body.size()
        );

        return ResponseEntity.ok(body);
    }


    @PostMapping("/duplication-candidates")
    public ResponseEntity<List<PatientBasicInformationResponseVM>> getDuplicationCandidates(
            @RequestBody PatientDuplicationLookupDTO dto,
            @ParameterObject Pageable pageable
    ) {
        Page<Patient> page = patientService.findDuplicationCandidates(dto, pageable);

        HttpHeaders headers =
                PaginationUtil.generatePaginationHttpHeaders(
                        ServletUriComponentsBuilder.fromCurrentRequest(),
                        page
                );

        List<PatientBasicInformationResponseVM> body = page.getContent().stream()
                .map(PatientBasicInformationResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
