package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.AddressRepository;
import com.dazzle.asklepios.repository.PatientRepository;
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
        LOG.debug("Fetching all addresses for patientId={}", patientId);
        return addressRepository.findByPatientIdOrderByIsCurrentDescIdDesc(patientId);
    }



    public Address create(Long patientId, Address incoming) {
        LOG.info("[CREATE] Request to create Address for patientId={} payload={}", patientId, incoming);

        if (incoming == null) {
            throw new BadRequestAlertException("Address payload is required", "address", "payload.required");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException("Patient not found with id " + patientId, "patient", "notfound"));

        try {
            addressRepository.resetIsCurrentForPatient(patientId);

            Address entity = Address.builder()
                    .patient(patient)
                    .country(incoming.getCountry())
                    .stateProvince(incoming.getStateProvince())
                    .city(incoming.getCity())
                    .streetName(incoming.getStreetName())
                    .houseApartmentNumber(incoming.getHouseApartmentNumber())
                    .postalZipCode(incoming.getPostalZipCode())
                    .additionalAddressLine(incoming.getAdditionalAddressLine())
                    .countryId(incoming.getCountryId())
                    .isCurrent(true)
                    .build();

            Address saved = addressRepository.saveAndFlush(entity);
            LOG.info("Successfully created address id={} for patientId={}", saved.getId(), patientId);
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


    public Optional<Address> update(Long id, Address incoming) {
        LOG.info("[UPDATE] (versioning) Request to update Address id={} payload={}", id, incoming);

        if (incoming == null) {
            throw new BadRequestAlertException("Address payload is required", "address", "payload.required");
        }

        Address existing = addressRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException("Address not found with id " + id, "address", "notfound"));

        Patient patient = existing.getPatient();
        Long patientId = (patient != null ? patient.getId() : null);

        try {
            if (patientId != null) {
                addressRepository.resetIsCurrentForPatient(patientId);
            }

            existing.setIsCurrent(false);
            addressRepository.save(existing);

            Address newVersion = Address.builder()
                    .patient(patient)
                    .country(incoming.getCountry())
                    .stateProvince(incoming.getStateProvince())
                    .city(incoming.getCity())
                    .streetName(incoming.getStreetName())
                    .houseApartmentNumber(incoming.getHouseApartmentNumber())
                    .postalZipCode(incoming.getPostalZipCode())
                    .additionalAddressLine(incoming.getAdditionalAddressLine())
                    .countryId(incoming.getCountryId())
                    .isCurrent(true)
                    .build();

            Address savedNew = addressRepository.saveAndFlush(newVersion);

            LOG.info(
                    "Successfully versioned address: oldId={} now is_current=false, new current address id={} for patientId={}",
                    existing.getId(),
                    savedNew.getId(),
                    patientId
            );

            return Optional.of(savedNew);

        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            handleConstraintsOnCreateOrUpdate(constraintException);

            throw new BadRequestAlertException(
                    "Database constraint violated while updating address (check required fields or unique constraints).",
                    "address",
                    "db.constraint"
            );
        }
    }


    @Transactional(readOnly = true)
    public Address findCurrentByPatient(Long patientId) {
        LOG.debug("Fetching current address for patientId={}", patientId);

        return addressRepository
                .findFirstByPatientIdAndIsCurrentTrueOrderByIdDesc(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Current address not found for patientId " + patientId,
                        "address",
                        "current.notfound"
                ));
    }
    private void handleConstraintsOnCreateOrUpdate(RuntimeException constraintException) {
        Throwable root = getRootCause(constraintException);
        String message = (root != null ? root.getMessage() : constraintException.getMessage());
        String lower = (message != null ? message.toLowerCase() : "");

        LOG.error("Database constraint violation while saving address: {}", message, constraintException);

        if (lower.contains("uk_address_country_id")
                || lower.contains("unique constraint")
                || lower.contains("duplicate key")
                || lower.contains("duplicate entry")) {
            throw new BadRequestAlertException(
                    "An address with the same countryId already exists.",
                    "address",
                    "unique.countryId"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving address (check required fields or unique constraints).",
                "address",
                "db.constraint"
        );
    }
}
