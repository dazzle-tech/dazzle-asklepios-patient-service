package com.dazzle.asklepios.web.rest;
import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.service.AddressService;

import com.dazzle.asklepios.service.dto.patientAddress.AddressCreateDTO;
import com.dazzle.asklepios.service.dto.patientAddress.AddressUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.AddressResponseVM;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
            @Valid @RequestBody AddressCreateDTO dto
    ) {
        LOG.debug("REST create Address for patientId={} payload={}", patientId, dto);

        Address created = addressService.create(patientId, dto);

        LOG.debug(
                "REST create Address success id={} patientId={} isCurrent={}",
                created.getId(),
                patientId,
                created.getIsCurrent()
        );

        return ResponseEntity
                .created(URI.create("/api/patient/addresses/patient/" + patientId))
                .body(AddressResponseVM.ofEntity(created));
    }


    @PutMapping("/addresses/{id}")
    public ResponseEntity<AddressResponseVM> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddressUpdateDTO dto
    ) {
        if (dto == null) {
            throw new BadRequestAlertException("Address payload is required", "address", "payload.required");
        }

        if (dto.id() == null || !dto.id().equals(id)) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id",
                    "address",
                    "id.mismatch"
            );
        }

        Address updated = addressService.update(dto);
        return ResponseEntity.ok(AddressResponseVM.ofEntity(updated));
    }


    @GetMapping("/addresses/patient/{patientId}")
    public ResponseEntity<List<AddressResponseVM>> getAddressesByPatient(
            @PathVariable Long patientId
    ) {
        List<Address> list = addressService.findAllByPatient(patientId);

        List<AddressResponseVM> body = list.stream()
                .map(AddressResponseVM::ofEntity)
                .toList();

        return ResponseEntity.ok(body);
    }

    @GetMapping("/addresses/patient/{patientId}/current")
    public ResponseEntity<AddressResponseVM> getCurrentAddress(
            @PathVariable Long patientId
    ) {
        Address current = addressService.findCurrentByPatient(patientId);
        return ResponseEntity.ok(AddressResponseVM.ofEntity(current));
    }
}