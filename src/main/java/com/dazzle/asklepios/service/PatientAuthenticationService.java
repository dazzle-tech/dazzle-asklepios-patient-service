package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patient.PatientLoginDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientAuthenticationService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    public Authentication authenticate(PatientLoginDTO login) {

        Patient patient = patientRepository
                .findByMedicalRecordNumber(login.medicalRecordNumber())
                .orElseThrow(() -> new BadCredentialsException("Invalid medical record number or password"));

        if (!passwordEncoder.matches(login.password(), patient.getPassword())) {
            throw new BadCredentialsException("Invalid medical record number or password");
        }

        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_PATIENT")
        );

        return new UsernamePasswordAuthenticationToken(
                patient.getMedicalRecordNumber(),
                null,
                authorities
        );
    }

    @Transactional(readOnly = true)
    public Authentication authenticatePatient(Patient patient) {
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_PATIENT")
        );

        return new UsernamePasswordAuthenticationToken(
                patient.getMedicalRecordNumber(),
                null,
                authorities
        );
    }


}
