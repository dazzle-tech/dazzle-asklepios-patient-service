package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.SurveyOtpService;
import com.dazzle.asklepios.service.SurveyService;
import com.dazzle.asklepios.service.dto.patientPortal.PatientOtpRequestDTO;
import com.dazzle.asklepios.service.dto.patientPortal.PatientOtpVerifyDTO;
import com.dazzle.asklepios.service.dto.survey.SurveySubmitDTO;
import com.dazzle.asklepios.web.rest.vm.survey.SurveyFormVM;
import com.dazzle.asklepios.web.rest.vm.survey.SurveyOtpVerifiedVM;
import com.dazzle.asklepios.web.rest.vm.survey.SurveyResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The patient satisfaction survey. The app signs the patient in with the patient portal OTP endpoints
 * and submits with that login token; the web page uses the survey OTP endpoints below and submits
 * with their verificationToken. A survey can also be submitted without a patient.
 */
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final SurveyOtpService surveyOtpService;

    /** Public */
    @GetMapping("/survey/form")
    public ResponseEntity<SurveyFormVM> getForm() {
        return ResponseEntity.ok(surveyService.getForm());
    }

    /** Public (web page): the patient portal OTP without the portal secret, limited per ID number */
    @PostMapping("/survey/request-otp")
    public ResponseEntity<String> requestOtp(
            @Valid @RequestBody PatientOtpRequestDTO request
    ) {
        return ResponseEntity.ok(surveyOtpService.requestOtp(request));
    }

    /** Public (web page): returns the token the survey is submitted with when the OTP is right */
    @PostMapping("/survey/verify-otp")
    public ResponseEntity<SurveyOtpVerifiedVM> verifyOtp(
            @Valid @RequestBody PatientOtpVerifyDTO request
    ) {
        return ResponseEntity.ok(surveyOtpService.verifyOtp(request));
    }

    /** Public; linked to the patient by the app's login token or the web page's verificationToken */
    @PostMapping("/survey/submit")
    public ResponseEntity<Void> submit(@Valid @RequestBody SurveySubmitDTO dto) {
        surveyService.submit(dto);
        return ResponseEntity.ok().build();
    }

    /** Staff only (needs a login, unlike the endpoints above) */
    @GetMapping("/survey/responses")
    public ResponseEntity<Page<SurveyResponseVM>> getResponses(Pageable pageable) {
        return ResponseEntity.ok(surveyService.getResponses(pageable));
    }
}
