package com.dazzle.asklepios.web.rest.vm.survey;

import java.time.Instant;

/**
 * After the web survey OTP is verified: the name to greet the patient, and the token the survey is
 * submitted with. The token only links a survey to the patient; it is not a patient portal login token.
 */
public record SurveyOtpVerifiedVM(String patientName, String verificationToken, Instant expiresAt) {
}
