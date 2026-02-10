// AddressService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.AddressRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientAddress.AddressCreateDTO;
import com.dazzle.asklepios.service.dto.patientAddress.AddressUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class AddressService {

    private static final Logger LOG = LoggerFactory.getLogger(AddressService.class);

    private final AddressRepository addressRepository;
    private final PatientRepository patientRepository;

    public AddressService(AddressRepository addressRepository, PatientRepository patientRepository) {
        this.addressRepository = addressRepository;
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public List<Address> findAllByPatient(Long patientId) {
        LOG.debug("[FIND ALL] Fetching all addresses for patientId={}", patientId);
        return addressRepository.findByPatientIdOrderByIsCurrentDescIdDesc(patientId);
    }

    @Transactional(readOnly = true)
    public Address findCurrentByPatient(Long patientId) {
        LOG.debug("[FIND CURRENT] Fetching current address for patientId={}", patientId);

        return addressRepository
                .findFirstByPatientIdAndIsCurrentTrueOrderByIdDesc(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Current address not found",
                        "address",
                        "notfound"
                ));
    }

    public Address create(Long patientId, AddressCreateDTO dto) {
        LOG.info("[CREATE] Address for patientId={}, payload={}", patientId, dto);

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found",
                        "patient",
                        "notfound"
                ));

        try {
            resetIsCurrentForPatient(patientId);

            Address entity = Address.builder()
                    .patient(patient)
                    .locationJson(dto.locationJson())
                    .streetName(dto.streetName())
                    .houseApartmentNumber(dto.houseApartmentNumber())
                    .postalZipCode(dto.postalZipCode())
                    .additionalAddressLine(dto.additionalAddressLine())
                    .isCurrent(true)
                    .build();

            Address saved = addressRepository.saveAndFlush(entity);
            LOG.info("Successfully created Address id={} for patientId={}", saved.getId(), patientId);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            handleConstraintsOnCreateOrUpdate(constraintException);

            throw new BadRequestAlertException(
                    "Database constraint violated while saving address (check required fields or unique constraints).",
                    "address",
                    "db.constraint"
            );
        }
    }

    public Address update(AddressUpdateDTO dto) {
        LOG.info("[UPDATE] Address id={}, payload={}", dto.id(), dto);

        Address existing = addressRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Address not found",
                        "address",
                        "notfound"
                ));

        try {
            existing.setLocationJson(dto.locationJson());
            existing.setStreetName(dto.streetName());
            existing.setHouseApartmentNumber(dto.houseApartmentNumber());
            existing.setPostalZipCode(dto.postalZipCode());
            existing.setAdditionalAddressLine(dto.additionalAddressLine());

            if (dto.isCurrent() != null) {
                LOG.debug("Updating isCurrent for Address id={} to {}", dto.id(), dto.isCurrent());
                existing.setIsCurrent(dto.isCurrent());
            }

            Address saved = addressRepository.saveAndFlush(existing);
            LOG.info("Successfully updated Address id={}", saved.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            handleConstraintsOnCreateOrUpdate(constraintException);

            throw new BadRequestAlertException(
                    "Database constraint violated while updating address (check required fields or unique constraints).",
                    "address",
                    "db.constraint"
            );
        }
    }


    private void resetIsCurrentForPatient(Long patientId) {
        LOG.debug("[RESET CURRENT] Setting isCurrent=false for existing current addresses, patientId={}", patientId);

        List<Address> currentAddresses = addressRepository.findByPatientIdAndIsCurrentTrue(patientId);

        if (currentAddresses.isEmpty()) {
            LOG.debug("[RESET CURRENT] No current addresses found to reset, patientId={}", patientId);
            return;
        }

        currentAddresses.forEach(a -> a.setIsCurrent(false));
        addressRepository.flush();

        LOG.debug("[RESET CURRENT] Reset done. affectedCount={} patientId={}", currentAddresses.size(), patientId);
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException constraintException) {
        Throwable root = getRootCause(constraintException);
        String message = (root != null ? root.getMessage() : constraintException.getMessage());
        String lower = (message != null ? message.toLowerCase() : "");

        LOG.error("Database constraint violation while saving address: {}", message, constraintException);

        if (lower.contains("uk_address_patient_full_address")
                || lower.contains("unique constraint")
                || lower.contains("duplicate key")
                || lower.contains("duplicate entry")) {
            throw new BadRequestAlertException(
                    "This address already exists for the same patient.",
                    "address",
                    "unique.patient.fullAddress"
            );
        }

        if (lower.contains("fk_address_patient") || lower.contains("foreign key")) {
            throw new BadRequestAlertException(
                    "Invalid patient reference for address.",
                    "address",
                    "fk.patient"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving address (check required fields or unique constraints).",
                "address",
                "db.constraint"
        );
    }
}
