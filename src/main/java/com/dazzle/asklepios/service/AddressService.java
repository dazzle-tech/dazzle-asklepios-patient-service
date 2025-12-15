package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.AddressRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    public Address create(Long patientId, Address addressRequest) {
        LOG.info("[CREATE] Request to create Address for patientId={}, payload={}", patientId, addressRequest);

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> {
                    LOG.error("Patient not found with id={}", patientId);
                    return new NotFoundAlertException("Patient not found", "patient", "notfound");
                });

        try {
            LOG.debug("Resetting isCurrent flag for all addresses of patientId={}", patientId);
            addressRepository.resetIsCurrentForPatient(patientId);

            Address entity = Address.builder()
                    .patient(patient)
                    .locationJson(addressRequest.getLocationJson())
                    .streetName(addressRequest.getStreetName())
                    .houseApartmentNumber(addressRequest.getHouseApartmentNumber())
                    .postalZipCode(addressRequest.getPostalZipCode())
                    .additionalAddressLine(addressRequest.getAdditionalAddressLine())
                    .isCurrent(true)
                    .build();

            Address saved = addressRepository.saveAndFlush(entity);
            LOG.info("Successfully created Address id={} for patientId={}", saved.getId(), patientId);
            return saved;

        } catch (Exception exception) {
            LOG.error(
                    "Database constraint violation while creating Address for patientId={}: {}",
                    patientId,
                    exception.getMessage(),
                    exception
            );
            throw new BadRequestAlertException("Database constraint violation", "address", "db.constraint");
        }
    }

    public Optional<Address> update(Long id, Address addressRequest) {
        LOG.info("[UPDATE] Request to update Address id={} payload={}", id, addressRequest);

        Address existing = addressRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.error("Address not found with id={}", id);
                    return new NotFoundAlertException("Address not found", "address", "notfound");
                });

        try {
            existing.setLocationJson(addressRequest.getLocationJson());
            existing.setStreetName(addressRequest.getStreetName());
            existing.setHouseApartmentNumber(addressRequest.getHouseApartmentNumber());
            existing.setPostalZipCode(addressRequest.getPostalZipCode());
            existing.setAdditionalAddressLine(addressRequest.getAdditionalAddressLine());

            if (addressRequest.getIsCurrent() != null) {
                LOG.debug("Updating isCurrent flag for Address id={} to {}", id, addressRequest.getIsCurrent());
                existing.setIsCurrent(addressRequest.getIsCurrent());
            }

            Address saved = addressRepository.saveAndFlush(existing);
            LOG.info("Successfully updated Address id={}", saved.getId());

            return Optional.of(saved);

        } catch (Exception exception) {
            LOG.error(
                    "Database constraint violation while updating Address id={}: {}",
                    id,
                    exception.getMessage(),
                    exception
            );
            throw new BadRequestAlertException("Database error", "address", "db.constraint");
        }
    }

    @Transactional(readOnly = true)
    public Address findCurrentByPatient(Long patientId) {
        LOG.debug("[FIND CURRENT] Fetching current address for patientId={}", patientId);

        return addressRepository
                .findFirstByPatientIdAndIsCurrentTrueOrderByIdDesc(patientId)
                .orElseThrow(() -> {
                    LOG.error("Current address not found for patientId={}", patientId);
                    return new NotFoundAlertException("Current address not found", "address", "notfound");
                });
    }
}
