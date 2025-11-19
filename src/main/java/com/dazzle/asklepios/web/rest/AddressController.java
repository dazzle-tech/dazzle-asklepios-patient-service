package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.service.AddressService;
import com.dazzle.asklepios.web.rest.vm.address.AddressCreateVM;
import com.dazzle.asklepios.web.rest.vm.address.AddressResponseVM;
import com.dazzle.asklepios.web.rest.vm.address.AddressUpdateVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;


import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class AddressController {

    private static final Logger LOG = LoggerFactory.getLogger(AddressController.class);

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping("/addresses/patient/{patientId}")
    public ResponseEntity<AddressResponseVM> createAddress(
            @PathVariable Long patientId,
            @Valid @RequestBody AddressCreateVM vm
    ) {
        LOG.debug("REST create Address for patientId={} payload={}", patientId, vm);

        Address toCreate = Address.builder()
                .country(vm.country())
                .stateProvince(vm.stateProvince())
                .city(vm.city())
                .streetName(vm.streetName())
                .houseApartmentNumber(vm.houseApartmentNumber())
                .postalZipCode(vm.postalZipCode())
                .additionalAddressLine(vm.additionalAddressLine())
                .countryId(vm.countryId())
                .build();

        Address created = addressService.create(patientId, toCreate);
        AddressResponseVM body = AddressResponseVM.ofEntity(created);

        return ResponseEntity
                .created(URI.create("/api/address/addresses/" + created.getId()))
                .body(body);
    }


    @PutMapping("/addresses/{id}")
    public ResponseEntity<AddressResponseVM> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddressUpdateVM vm
    ) {
        LOG.debug("REST update Address id={} payload={}", id, vm);

        if (vm.id() == null || !vm.id().equals(id)) {
            throw new com.dazzle.asklepios.web.rest.errors.BadRequestAlertException(
                    "Invalid id", "address", "idinvalid"
            );
        }

        Address patch = new Address();
        patch.setId(vm.id());
        patch.setCountry(vm.country());
        patch.setStateProvince(vm.stateProvince());
        patch.setCity(vm.city());
        patch.setStreetName(vm.streetName());
        patch.setHouseApartmentNumber(vm.houseApartmentNumber());
        patch.setPostalZipCode(vm.postalZipCode());
        patch.setAdditionalAddressLine(vm.additionalAddressLine());
        patch.setCountryId(vm.countryId());

        return addressService.update(id, patch)
                .map(AddressResponseVM::ofEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @GetMapping("/addresses/patient/{patientId}")
    public ResponseEntity<List<AddressResponseVM>> getAddressesByPatient(
            @PathVariable Long patientId
    ) {
        LOG.debug("REST list Addresses for patientId={}", patientId);

        List<Address> list = addressService.findAllByPatient(patientId);
        List<AddressResponseVM> body = list.stream()
                .map(AddressResponseVM::ofEntity)
                .toList();

        return ResponseEntity.ok(body);
    }


    @GetMapping("/addresses/patient/{patientId}/current")
    public ResponseEntity<AddressResponseVM> getCurrentAddress(@PathVariable Long patientId) {
        LOG.debug("REST get current Address for patientId={}", patientId);

        Address current = addressService.findCurrentByPatient(patientId);
        AddressResponseVM body = AddressResponseVM.ofEntity(current);

        return ResponseEntity.ok(body);
    }
}
