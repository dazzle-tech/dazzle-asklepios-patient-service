package com.dazzle.asklepios.integration.nahdi.controller;

import com.dazzle.asklepios.integration.nahdi.domain.enumeration.NahdiIntegrationStatus;
import com.dazzle.asklepios.integration.nahdi.dto.NahdiIntegrationLogDTO;
import com.dazzle.asklepios.integration.nahdi.service.NahdiIntegrationLogService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * REST controller for reading {@link com.dazzle.asklepios.integration.nahdi.domain.NahdiIntegrationLog}.
 * Logs are written by the integration itself, so this resource is read-only.
 */
@RestController
@RequestMapping("/api/patient")
public class NahdiIntegrationLogController {

    private static final Logger LOG = LoggerFactory.getLogger(NahdiIntegrationLogController.class);

    private static final String ENTITY_NAME = "nahdiIntegrationLog";

    private final NahdiIntegrationLogService nahdiIntegrationLogService;

    public NahdiIntegrationLogController(NahdiIntegrationLogService nahdiIntegrationLogService) {
        this.nahdiIntegrationLogService = nahdiIntegrationLogService;
    }

    @GetMapping("/nahdi-integration-logs")
    public ResponseEntity<List<NahdiIntegrationLogDTO>> getAll(
            @RequestParam(required = false) NahdiIntegrationStatus status,
            Pageable pageable
    ) {
        LOG.debug("REST request to get NahdiIntegrationLogs status={}", status);
        Page<NahdiIntegrationLogDTO> page = nahdiIntegrationLogService.findAll(status, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    @GetMapping("/nahdi-integration-logs/{id}")
    public ResponseEntity<NahdiIntegrationLogDTO> get(@PathVariable Long id) {
        LOG.debug("REST request to get NahdiIntegrationLog id={}", id);
        return nahdiIntegrationLogService.findOne(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundAlertException("NahdiIntegrationLog not found: " + id, ENTITY_NAME, "notfound"));
    }
}
