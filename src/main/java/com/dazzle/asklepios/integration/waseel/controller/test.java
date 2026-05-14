package com.dazzle.asklepios.integration.waseel.controller;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import com.dazzle.asklepios.integration.waseel.service.WaseelCchiService;
import com.dazzle.asklepios.integration.waseel.service.WaseelTokenService;
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
public class test {

    private WaseelTokenService waseelTokenService;
    private final WaseelCchiService waseelCchiService;

    public test( WaseelTokenService waseelTokenService,WaseelCchiService waseelCchiService) {
        this.waseelTokenService = waseelTokenService;
        this.waseelCchiService = waseelCchiService;


    }

    @GetMapping("/internal/waseel/token-test")
    public String testWaseelToken() {
        return waseelTokenService.getToken() != null ? "Waseel token received" : "No token";
    }

    @GetMapping("/internal/waseel/cchi/{documentId}")
    public CchiInquiryResponse testCchi(@PathVariable String documentId) {
        return waseelCchiService.fetchBeneficiaryByDocumentId(documentId);
    }

}