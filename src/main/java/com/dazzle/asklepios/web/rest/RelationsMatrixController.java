package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.RelationsMatrix;
import com.dazzle.asklepios.domain.enumeration.Gender;
import com.dazzle.asklepios.domain.enumeration.RelationType;
import com.dazzle.asklepios.service.RelationsMatrixService;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.relation.RelationsMatrixResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/patient/relations-matrix")
public class RelationsMatrixController {

    private static final Logger LOG = LoggerFactory.getLogger(RelationsMatrixController.class);

    private final RelationsMatrixService matrixService;

    public RelationsMatrixController(RelationsMatrixService matrixService) {
        this.matrixService = matrixService;
    }

    /**
     * GET /relations-matrix/by-first-gender/{gender}
     */
    @GetMapping("/by-first-gender/{gender}")
    public ResponseEntity<List<RelationsMatrixResponseVM>> getByFirstGender(
            @PathVariable Gender gender,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get RelationsMatrix by firstGender={} page={}", gender, pageable);

        Page<RelationsMatrix> page = matrixService.findByFirstGender(gender, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        List<RelationsMatrixResponseVM> body = page.getContent()
                .stream()
                .map(RelationsMatrixResponseVM::fromEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /**
     * GET /relations-matrix/by-genders?first=MALE&second=FEMALE
     */
    @GetMapping("/by-genders")
    public ResponseEntity<List<RelationsMatrixResponseVM>> getByFirstAndSecondGender(
            @RequestParam Gender first,
            @RequestParam Gender second,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get RelationsMatrix by first={} second={} page={}", first, second, pageable);

        Page<RelationsMatrix> page = matrixService.findByFirstAndSecondGender(first, second, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        List<RelationsMatrixResponseVM> body = page.getContent()
                .stream()
                .map(RelationsMatrixResponseVM::fromEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }


}
