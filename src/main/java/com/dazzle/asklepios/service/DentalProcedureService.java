package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalProcedure;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.DentalProcedure.DentalProcedureResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class DentalProcedureService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalProcedureService.class);

    private final DentalProcedureRepository dentalProcedureRepository;

    public DentalProcedureService(DentalProcedureRepository dentalProcedureRepository) {
        this.dentalProcedureRepository = dentalProcedureRepository;
    }

    public DentalProcedureResponseVM create(DentalProcedureCreateDTO dto) {
        LOG.debug("Request to create DentalProcedure : {}", dto);

        DentalProcedure entity = DentalProcedure.builder()
                .patientId(dto.patientId())
                .encounterId(dto.encounterId())
                .toothNumber(dto.toothNumber())
                .surface(dto.surface())
                .anesthesiaUsed(dto.anesthesiaUsed())
                .dose(dto.dose())
                .unit(dto.unit())
                .fillingMaterial(dto.fillingMaterial())
                .serviceId(dto.serviceId())
                .cdtCodeId(dto.cdtCodeId())
                .notes(dto.notes())
                .cancelled(false)
                .build();

        try {
            DentalProcedure saved = dentalProcedureRepository.save(entity);
            LOG.debug("Created DentalProcedure: {}", saved);
            return DentalProcedureResponseVM.ofEntity(saved);
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            throw handleConstraintViolation(e);
        }
    }

    @Transactional(readOnly = true)
    public Page<DentalProcedureResponseVM> findAllByPatientId(
            Long patientId,
            boolean showCancelled,
            Pageable pageable
    ) {
        Page<DentalProcedure> page;
        if (showCancelled) {
            LOG.debug("Fetch DentalProcedures with cancelled for patientId={}", patientId);
            page = dentalProcedureRepository.findByPatientId(patientId, pageable);
        } else {
            LOG.debug("Fetch DentalProcedures without cancelled for patientId={}", patientId);
            page = dentalProcedureRepository.findByPatientIdAndCancelledFalse(patientId, pageable);
        }
        return page.map(DentalProcedureResponseVM::ofEntity);
    }

    @Transactional
    public DentalProcedureResponseVM update(DentalProcedureUpdateDTO dto) {
        LOG.debug("Request to update DentalProcedure : {}", dto);

        DentalProcedure entity = dentalProcedureRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "dentalProcedure",
                        "DentalProcedure not found with id " + dto.id()
                ));

        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "cannotUpdateCancelled",
                    "dentalProcedure",
                    "Cannot update a cancelled dental procedure"
            );
        }

        entity.setToothNumber(dto.toothNumber());
        entity.setSurface(dto.surface());
        entity.setAnesthesiaUsed(dto.anesthesiaUsed());
        entity.setDose(dto.dose());
        entity.setUnit(dto.unit());
        entity.setFillingMaterial(dto.fillingMaterial());
        entity.setServiceId(dto.serviceId());
        entity.setCdtCodeId(dto.cdtCodeId());
        entity.setNotes(dto.notes());

        try {
            DentalProcedure updated = dentalProcedureRepository.saveAndFlush(entity);
            LOG.debug("Updated DentalProcedure: {}", updated);
            return DentalProcedureResponseVM.ofEntity(updated);
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            throw handleConstraintViolation(e);
        }
    }

    @Transactional
    public DentalProcedureResponseVM cancel(Long id) {
        LOG.debug("Request to cancel DentalProcedure : {}", id);

        SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        DentalProcedure entity = dentalProcedureRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "dentalProcedure",
                        "DentalProcedure not found with id " + id
                ));

        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "alreadyCancelled",
                    "dentalProcedure",
                    "DentalProcedure is already cancelled"
            );
        }

        entity.setCancelled(true);
        LOG.debug("Cancelled DentalProcedure: {}", entity);
        return DentalProcedureResponseVM.ofEntity(entity);
    }

    private BadRequestAlertException handleConstraintViolation(RuntimeException e) {
        Throwable root = getRootCause(e);
        String message = (root != null ? root.getMessage() : e.getMessage());
        String msgLower = message != null ? message.toLowerCase() : "";

        LOG.error("DB constraint violation while saving DentalProcedure: {}", message, e);

        if (msgLower.contains("fk_dental_procedure_service")) {
            return new BadRequestAlertException(
                    "serviceNotFound",
                    "dentalProcedure",
                    "The specified service does not exist"
            );
        }
        if (msgLower.contains("fk_dental_procedure_cdt_code")) {
            return new BadRequestAlertException(
                    "cdtCodeNotFound",
                    "dentalProcedure",
                    "The specified CDT code does not exist"
            );
        }

        return new BadRequestAlertException(
                "db.constraint",
                "dentalProcedure",
                "Database constraint violated while saving dental procedure"
        );
    }
}