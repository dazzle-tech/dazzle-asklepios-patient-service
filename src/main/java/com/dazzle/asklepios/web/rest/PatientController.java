package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.service.PatientAuthenticationService;
import com.dazzle.asklepios.service.PatientService;
import com.dazzle.asklepios.service.dto.patient.*;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.InvalidPasswordException;
import com.dazzle.asklepios.web.rest.vm.patient.CreatePasswordKeyValidationVM;
import com.dazzle.asklepios.web.rest.vm.patient.ManagedPatientVM;
import com.dazzle.asklepios.web.rest.vm.patient.PatientBasicInformationResponseVM;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static com.dazzle.asklepios.security.SecurityUtils.AUTHORITIES_CLAIM;
import static com.dazzle.asklepios.security.SecurityUtils.JWT_ALGORITHM;

@RestController
@RequestMapping("/api/patient")

public class PatientController {

    private static final Logger LOG = LoggerFactory.getLogger(PatientController.class);

    private final PatientService patientService;
    private final PatientAuthenticationService patientAuthenticationService;

    private final JwtEncoder jwtEncoder;

    @Value("${patient.security.authentication.jwt.token-validity-in-seconds:86400}")
    private long tokenValidityInSeconds;

    @Value("${patient.security.authentication.jwt.token-validity-in-seconds-for-remember-me:2592000}")
    private long tokenValidityInSecondsForRememberMe;

    public PatientController(PatientService patientService, PatientAuthenticationService patientAuthenticationService, JwtEncoder jwtEncoder) {
        this.patientService = patientService;
        this.patientAuthenticationService = patientAuthenticationService;
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping
    public ResponseEntity<Patient> createPatient(@Valid @RequestBody PatientCreateDTO patientDTO) {
        LOG.debug("REST create Patient payload={}", patientDTO);

        if (patientDTO == null) {
            throw new BadRequestAlertException("Patient payload is required", "patient", "payload.required");
        }

        Patient created = patientService.create(patientDTO);
        return ResponseEntity.created(URI.create("/api/patient/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatient(@PathVariable Long id, @Valid @RequestBody PatientUpdateDTO patientDTO) {
        LOG.debug("REST update Patient id={} payload={}", id, patientDTO);

        if (patientDTO == null) {
            throw new BadRequestAlertException("Patient payload is required", "patient", "payload.required");
        }

        if (patientDTO.id() == null || !patientDTO.id().equals(id)) {
            throw new BadRequestAlertException("Path id does not match payload id", "patient", "id.mismatch");
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
    public ResponseEntity<List<Patient>> getByMedicalRecordNumber(@PathVariable String medicalRecordNumber, @ParameterObject Pageable pageable) {
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
    public ResponseEntity<List<Patient>> getByArchivingNumber(@PathVariable String archivingNumber, @ParameterObject Pageable pageable) {
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
    public ResponseEntity<List<Patient>> getByPrimaryPhone(@PathVariable("phone") String phone, @ParameterObject Pageable pageable) {
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

    @GetMapping("/patients")
    public ResponseEntity<List<Patient>> getPatients(@ParameterObject Pageable pageable) {

        Page<Patient> page = patientService.findAll(pageable);

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

    @GetMapping("/facility-patients")
    public ResponseEntity<List<Patient>> getFacilityPatients(

            @RequestParam(required = false) String patientName,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate registrationDateFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate registrationDateTo,

            @RequestParam(required = false) Long insuranceId,

            @ParameterObject Pageable pageable
    ) {

        FacilityPatientFilterDTO filter = new FacilityPatientFilterDTO(
                patientName,
                registrationDateFrom,
                registrationDateTo,
                insuranceId
        );

        Page<Patient> page =
                patientService.findFacilityPatients(filter, pageable);

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
    public ResponseEntity<List<Patient>> getByDateOfBirth(@PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth, @ParameterObject Pageable pageable) {
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
    public ResponseEntity<List<Patient>> getByFullName(@PathVariable("keyword") String keyword, @ParameterObject Pageable pageable) {
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
    public ResponseEntity<List<Patient>> getPatientsByPrimaryDocumentNumber(@RequestParam("number") String numberPart, @ParameterObject Pageable pageable) {
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
    public ResponseEntity<List<Patient>> getPatientsByAnyDocumentNumber(@RequestParam("number") String numberPart, @ParameterObject Pageable pageable) {
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
    public ResponseEntity<Page<Patient>> getUnknownPatients(@ParameterObject Pageable pageable) {
        LOG.info("REST request to get unknown patients - page: {}, size: {}, sort: {}",
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort());

        Page<Patient> page = patientService.findUnknownPatients(pageable);

        LOG.info("REST request result for unknown patients - returned elements: {}, total elements: {}, total pages: {}",
                page.getNumberOfElements(),
                page.getTotalElements(),
                page.getTotalPages());

        return ResponseEntity.ok(page);
    }

    @PostMapping("/bulk/basic-info")
    public ResponseEntity<List<PatientBasicInformationResponseVM>> getBulkPatientBasicInfo(@RequestBody List<Long> ids) {
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
    public ResponseEntity<List<PatientBasicInformationResponseVM>> getDuplicationCandidates(@RequestBody PatientDuplicationLookupDTO duplicationLookupDTO, @ParameterObject Pageable pageable) {
        Page<Patient> page = patientService.findDuplicationCandidates(duplicationLookupDTO, pageable);

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

    @PostMapping("/{id}/send-create-password")
    public ResponseEntity<Void> sendCreatePasswordEmailToPatient(@PathVariable Long id) {
        LOG.debug("REST send create-password email to Patient id={}", id);
        patientService.sendCreatePasswordEmailToPatient(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatientById(@PathVariable Long id) {
        LOG.debug("REST get Patient by id={}", id);
        Patient patient = patientService.findById(id);
        return ResponseEntity.ok(patient);
    }

    @GetMapping("/by-ids")
    public ResponseEntity<List<Patient>> getPatientsByIds(@RequestParam List<Long> ids) {
        LOG.debug("REST get Patients by ids={}", ids);
        List<Patient> patients = patientService.findByIds(ids);
        return ResponseEntity.ok(patients);
    }

    @PostMapping(path = "/create-patient-password/finish")
    public ResponseEntity<Void> finishCreatePassword(@RequestBody KeyAndPasswordDTO keyAndPassword) {
        if (isPasswordLengthInvalid(keyAndPassword.getNewPassword())) {
            throw new InvalidPasswordException();
        }

        patientService
                .completeCreatePassword(keyAndPassword.getNewPassword(), keyAndPassword.getKey())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "patient",
                        "No user was found for this create-password key"
                ));

        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/create-patient-password/validate", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreatePasswordKeyValidationVM validate(@RequestParam("key") String key) {
        return patientService.validateCreatePasswordKey(key);
    }

    @PostMapping("/authenticate")
    public ResponseEntity<JWTToken> authenticatePatient(@Valid @RequestBody PatientLoginDTO login) {

        Authentication authentication = patientAuthenticationService.authenticate(login);

        String jwt = createToken(
                authentication,
                Boolean.TRUE.equals(login.rememberMe()));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);

        return new ResponseEntity<>(new JWTToken(jwt), headers, HttpStatus.OK);

    }

    private static boolean isPasswordLengthInvalid(String password) {
        return (
                StringUtils.isEmpty(password) ||
                        password.length() < ManagedPatientVM.PASSWORD_MIN_LENGTH ||
                        password.length() > ManagedPatientVM.PASSWORD_MAX_LENGTH
        );
    }

    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        Instant now = Instant.now();
        Instant validity = rememberMe
                ? now.plus(tokenValidityInSecondsForRememberMe, ChronoUnit.SECONDS)
                : now.plus(tokenValidityInSeconds, ChronoUnit.SECONDS);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(validity)
                .subject(authentication.getName())
                .claim(AUTHORITIES_CLAIM, authorities);

        if (authentication.getPrincipal() instanceof Patient patient) {
            claims.claim("patientId", patient.getId());
            claims.claim("medicalRecordNumber", patient.getMedicalRecordNumber());
        }

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(jwsHeader, claims.build())
        ).getTokenValue();
    }
    /**
     * Object to return as body in JWT Authentication.
     */
    static class JWTToken {

        private String idToken;

        JWTToken(String idToken) {
            this.idToken = idToken;
        }

        @JsonProperty("id_token")
        String getIdToken() {
            return idToken;
        }

        void setIdToken(String idToken) {
            this.idToken = idToken;
        }
    }
}


