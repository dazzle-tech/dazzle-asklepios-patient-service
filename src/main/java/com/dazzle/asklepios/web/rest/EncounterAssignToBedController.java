package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.BedTransaction;
import com.dazzle.asklepios.domain.EncounterAssignToBed;
import com.dazzle.asklepios.service.BedTransactionService;
import com.dazzle.asklepios.service.EncounterAssignToBedService;
import com.dazzle.asklepios.service.dto.encounterAssignToBed.EncounterAssignToBedCreateDTO;
import com.dazzle.asklepios.service.dto.encounterAssignToBed.EncounterAssignToBedUpdateDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class EncounterAssignToBedController {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAssignToBedController.class);

    private final EncounterAssignToBedService encounterAssignToBedService;
    private final BedTransactionService bedTransactionService;
    public EncounterAssignToBedController(EncounterAssignToBedService encounterAssignToBedService, BedTransactionService bedTransactionService) {
        this.encounterAssignToBedService = encounterAssignToBedService;
        this.bedTransactionService = bedTransactionService;
    }

    @PostMapping("/encounter-assign-to-bed")
    public ResponseEntity<EncounterAssignToBed> createEncounterAssignToBed(
            @Valid @RequestBody @NotNull EncounterAssignToBedCreateDTO createDTO
    ) {
        LOG.debug("REST create EncounterAssignToBed payload={}", createDTO);

        EncounterAssignToBed createdEncounterAssignToBed =
                encounterAssignToBedService.create(createDTO);

        return ResponseEntity
                .created(URI.create("/api/patient/encounter-assign-to-bed/" + createdEncounterAssignToBed.getId()))
                .body(createdEncounterAssignToBed);
    }

    @PutMapping("/encounter-assign-to-bed/{id}")
    public ResponseEntity<EncounterAssignToBed> updateEncounterAssignToBed(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody @NotNull EncounterAssignToBedUpdateDTO updateDTO
    ) {
        LOG.debug("REST update EncounterAssignToBed id={} payload={}", id, updateDTO);

        EncounterAssignToBed updatedEncounterAssignToBed =
                encounterAssignToBedService.update(id, updateDTO);

        return ResponseEntity.ok(updatedEncounterAssignToBed);
    }

    @PutMapping("/encounter-assign-to-bed/{id}/release")
    public ResponseEntity<EncounterAssignToBed> releaseEncounterAssignToBed(
            @PathVariable @NotNull Long id
    ) {
        LOG.debug("REST release EncounterAssignToBed id={}", id);

        EncounterAssignToBed releasedEncounterAssignToBed =
                encounterAssignToBedService.release(id);

        return ResponseEntity.ok(releasedEncounterAssignToBed);
    }

    @GetMapping("/encounter-assign-to-bed/{id}")
    public ResponseEntity<EncounterAssignToBed> getEncounterAssignToBedById(
            @PathVariable @NotNull Long id
    ) {
        LOG.debug("REST get EncounterAssignToBed by id={}", id);

        EncounterAssignToBed encounterAssignToBed = encounterAssignToBedService.getById(id);

        return ResponseEntity.ok(encounterAssignToBed);
    }


    @GetMapping("/encounter-assign-to-bed/active/by-encounter/{encounterId}")
    public ResponseEntity<EncounterAssignToBed> getActiveAssignmentByEncounterId(
            @PathVariable @NotNull Long encounterId
    ) {
        LOG.debug("REST get active EncounterAssignToBed by encounterId={}", encounterId);

        EncounterAssignToBed activeEncounterAssignToBed =
                encounterAssignToBedService.getActiveAssignmentByEncounterId(encounterId);

        return ResponseEntity.ok(activeEncounterAssignToBed);
    }

    @GetMapping("/encounter-assign-to-bed/active/bed-ids")
    public ResponseEntity<List<Long>> getActiveBedIds() {
        LOG.debug("REST get active bed ids");

        List<Long> activeBedIds = encounterAssignToBedService.getActiveBedIds();

        return ResponseEntity.ok(activeBedIds);
    }

    @GetMapping("/encounter-assign-to-bed/active/room-ids")
    public ResponseEntity<List<Long>> getActiveRoomIds() {
        LOG.debug("REST get active room ids");

        List<Long> activeRoomIds = encounterAssignToBedService.getActiveRoomIds();

        return ResponseEntity.ok(activeRoomIds);
    }

    @GetMapping("/encounter-assign-to-bed/active-list/by-encounters")
    public ResponseEntity<List<EncounterAssignToBed>> getActiveAssignmentsByEncounterIds(
            @RequestParam @NotNull List<Long> encounterIds
    ) {
        LOG.debug("REST get active assignments list by encounterIds={}", encounterIds);

        List<EncounterAssignToBed> activeAssignments =
                encounterAssignToBedService.getActiveAssignmentsByEncounterIds(encounterIds);

        return ResponseEntity.ok(activeAssignments);
    }
    @GetMapping("/bed-transaction/search/by-department-and-date")
    public ResponseEntity<List<BedTransaction>> getByDepartmentAndDateRange(
            @RequestParam @NotNull  Long departmentId,
            @RequestParam @NotNull  Instant from,
            @RequestParam @NotNull  Instant to,
            @ParameterObject  Pageable pageable
    ) {
        LOG.debug("REST get BedTransactions by departmentId={} from={} to={} pageable={}",
                departmentId, from, to, pageable);

        Page<BedTransaction> page =
                bedTransactionService.getByToDepartmentAndTransactionDateRange(
                        departmentId, from, to, pageable
                );

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(),
                page
        );

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }
}