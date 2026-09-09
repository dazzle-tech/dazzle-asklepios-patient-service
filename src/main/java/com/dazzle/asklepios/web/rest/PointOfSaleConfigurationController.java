package com.dazzle.asklepios.web.rest;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiRegisterTerminalResponse;
import com.dazzle.asklepios.domain.PointOfSaleConfiguration;
import com.dazzle.asklepios.service.PointOfSaleConfigurationService;
import com.dazzle.asklepios.service.dto.pointOfSale.PointOfSaleConfigurationDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

        import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PointOfSaleConfigurationController {
    private static final Logger LOG = LoggerFactory.getLogger(PointOfSaleConfigurationController.class);

    private final PointOfSaleConfigurationService pointOfSaleConfigurationService;

    @PostMapping("/pos-configration")
    public ResponseEntity<PointOfSaleConfigurationDTO> create(
            @Valid @RequestBody PointOfSaleConfigurationDTO request
    ) {

        return ResponseEntity.ok(
                pointOfSaleConfigurationService.create(request)
        );
    }

    @PutMapping("/pos-configration/{id}")
    public ResponseEntity<PointOfSaleConfigurationDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody PointOfSaleConfigurationDTO request
    ) {

        return ResponseEntity.ok(
                pointOfSaleConfigurationService.update(id, request)
        );
    }

    @GetMapping("/pos-configration")
    public ResponseEntity<List<PointOfSaleConfigurationDTO>> findAll(
            @RequestParam(required = false)
            Boolean isActive
    ) {

        return ResponseEntity.ok(
                pointOfSaleConfigurationService.findAll(isActive)
        );
    }

    @GetMapping("/pos-configration/{id}")
    public ResponseEntity<PointOfSaleConfigurationDTO> findOne(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                pointOfSaleConfigurationService.findOne(id)
        );
    }
    @PatchMapping("/pos-configration/{id}/toggle-active")
    public ResponseEntity<PointOfSaleConfigurationDTO> toggleActive(
            @PathVariable Long id
    ) {

        LOG.debug(
                "REST request to toggle POS Configuration active status: id={}",
                id
        );

        return ResponseEntity.ok(
                pointOfSaleConfigurationService.toggleActive(id)
        );
    }
    @PostMapping("/pos-configration/{id}/register")
    public ResponseEntity<NamiRegisterTerminalResponse> registerTerminal(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                pointOfSaleConfigurationService.registerTerminal(id)
        );
    }



}