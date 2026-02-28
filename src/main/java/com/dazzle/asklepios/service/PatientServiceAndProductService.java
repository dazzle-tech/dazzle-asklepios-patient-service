package com.dazzle.asklepios.service;


import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PatientServiceCategory;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.web.rest.dto.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.web.rest.dto.PatientServiceProductUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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

import java.time.Instant;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;


@Service
@Transactional
public class PatientServiceAndProductService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceAndProductService.class);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    public PatientServiceAndProductService(PatientServiceAndProductRepository patientServiceAndProductRepository) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
    }


    public PatientServiceAndProduct create(PatientServiceProductCreateDTO patientServiceProductCreateDTO) {

        LOG.debug("Request to create Patient Service/Product : {}", patientServiceProductCreateDTO);

        // Category rules
        if (patientServiceProductCreateDTO.category() == PatientServiceCategory.SERVICE) {

            if (patientServiceProductCreateDTO.serviceId() == null) {
                LOG.debug("service_id is null while category is SERVICE : {}", patientServiceProductCreateDTO);
                throw new BadRequestAlertException(
                        "serviceIdRequired",
                        "patientServicesAndProducts",
                        "Service is required when category is SERVICE"
                );
            }

            if (patientServiceProductCreateDTO.productId() != null) {
                LOG.debug("product_id is not null while category is SERVICE : {}", patientServiceProductCreateDTO);
                throw new BadRequestAlertException(
                        "productMustBeNull",
                        "patientServicesAndProducts",
                        "Product must be null when category is SERVICE"
                );
            }
        }

        if (patientServiceProductCreateDTO.category() == PatientServiceCategory.PRODUCT) {

            if (patientServiceProductCreateDTO.productId() == null) {
                LOG.debug("product_id is null while category is PRODUCT : {}", patientServiceProductCreateDTO);
                throw new BadRequestAlertException(
                        "productIdRequired",
                        "patientServicesAndProducts",
                        "Product is required when category is PRODUCT"
                );
            }

            if (patientServiceProductCreateDTO.serviceId() != null) {
                LOG.debug("service_id is not null while category is PRODUCT : {}", patientServiceProductCreateDTO);
                throw new BadRequestAlertException(
                        "serviceMustBeNull",
                        "patientServicesAndProducts",
                        "Service must be null when category is PRODUCT"
                );
            }
        }

        PatientServiceAndProduct entity = PatientServiceAndProduct.builder()
                .patientId(patientServiceProductCreateDTO.patientId())
                .encounterId(patientServiceProductCreateDTO.encounterId())
                .category(patientServiceProductCreateDTO.category())
                .serviceId(patientServiceProductCreateDTO.serviceId())
                .productId(patientServiceProductCreateDTO.productId())
                .quantity(patientServiceProductCreateDTO.quantity() == null ? 1L : patientServiceProductCreateDTO.quantity())
                .build();

        entity.setCreatedDate(Instant.now());
        entity.setCreatedBy(getCurrentUser());
        entity.setLastModifiedDate(Instant.now());
        entity.setLastModifiedBy(getCurrentUser());

        try {
            PatientServiceAndProduct saved = patientServiceAndProductRepository.save(entity);
            LOG.debug("Created Patient Service/Product : {}", saved);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }


    @Transactional(readOnly = true)
    public Page<PatientServiceAndProduct> findAllServicesAndProductsByEncounterId(
            Pageable pageable,
            Long encounterId
    ) {

        LOG.debug("Fetch Patient Services & Products for encounter : {}", encounterId);

        return patientServiceAndProductRepository.findAllByEncounterId(encounterId, pageable);
    }


    @Transactional
    public PatientServiceAndProduct update(PatientServiceProductUpdateDTO patientServiceProductUpdateDTO) {

        LOG.debug("Request to update Patient Service/Product : {}", patientServiceProductUpdateDTO);

        PatientServiceAndProduct entity = patientServiceAndProductRepository.findById(patientServiceProductUpdateDTO.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "patientServicesAndProducts",
                        "Record not found with id " + patientServiceProductUpdateDTO.id()
                ));

        // ================= VALIDATIONS =================

        if (patientServiceProductUpdateDTO.category() == PatientServiceCategory.SERVICE) {

            if (patientServiceProductUpdateDTO.serviceId() == null) {
                LOG.debug("updated service_id is null while category is SERVICE : {}", patientServiceProductUpdateDTO);
                throw new BadRequestAlertException(
                        "serviceIdRequired",
                        "patientServicesAndProducts",
                        "Service is required when category is SERVICE"
                );
            }

            if (patientServiceProductUpdateDTO.productId() != null) {
                LOG.debug("updated product_id is not null while category is SERVICE : {}", patientServiceProductUpdateDTO);
                throw new BadRequestAlertException(
                        "productMustBeNull",
                        "patientServicesAndProducts",
                        "Product must be null when category is SERVICE"
                );
            }
        }

        if (patientServiceProductUpdateDTO.category() == PatientServiceCategory.PRODUCT) {

            if (patientServiceProductUpdateDTO.productId() == null) {
                LOG.debug("updated product_id is null while category is PRODUCT : {}", patientServiceProductUpdateDTO);
                throw new BadRequestAlertException(
                        "productIdRequired",
                        "patientServicesAndProducts",
                        "Product is required when category is PRODUCT"
                );
            }

            if (patientServiceProductUpdateDTO.serviceId() != null) {
                LOG.debug("updated service_id is not null while category is PRODUCT : {}", patientServiceProductUpdateDTO);
                throw new BadRequestAlertException(
                        "serviceMustBeNull",
                        "patientServicesAndProducts",
                        "Service must be null when category is PRODUCT"
                );
            }
        }

        entity.setCategory(patientServiceProductUpdateDTO.category());
        entity.setServiceId(patientServiceProductUpdateDTO.serviceId());
        entity.setProductId(patientServiceProductUpdateDTO.productId());
        entity.setQuantity(patientServiceProductUpdateDTO.quantity());
        entity.setLastModifiedBy(getCurrentUser());
        entity.setLastModifiedDate(Instant.now());

        try {
            PatientServiceAndProduct updated = patientServiceAndProductRepository.saveAndFlush(entity);
            LOG.debug("Updated Patient Service/Product : {}", updated);
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional
    public void remove(Long id) {

        LOG.debug("Request to delete Patient Service/Product : {}", id);

        PatientServiceAndProduct entity = patientServiceAndProductRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "patientServicesAndProducts",
                        "Record not found"
                ));

        patientServiceAndProductRepository.delete(entity);

        LOG.debug("Deleted Patient Service/Product : id={}", id);
    }


    private BadRequestAlertException handleConstraintViolation(RuntimeException ex) {

        Throwable root = getRootCause(ex);
        String message = (root != null ? root.getMessage() : ex.getMessage());
        String msgLower = message != null ? message.toLowerCase() : "";

        LOG.error("Database constraint violation: {}", message, ex);

        if (msgLower.contains("uk_patient_product")) {
            return new BadRequestAlertException(
                    "duplicate.product",
                    "patient_services_and_products",
                    "This product already exists"
            );
        }

        if (msgLower.contains("uk_patient_service")) {
            return new BadRequestAlertException(
                    "duplicate.service",
                    "patient_services_and_products",
                    "This service already exists"
            );
        }

        if (msgLower.contains("fk_patient_services_and_products_product_id")) {
            return new BadRequestAlertException(
                    "productNotFound",
                    "patient_services_and_products",
                    "Product does not exist"
            );
        }

        if (msgLower.contains("fk_patient_services_and_products_service_id")) {
            return new BadRequestAlertException(
                    "serviceNotFound",
                    "patient_services_and_products",
                    "Service does not exist"
            );
        }

        return new BadRequestAlertException(
                "db.constraint",
                "patient_services_and_products",
                "Database constraint violated while saving patient service/product"
        );
    }


    private String getCurrentUser() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not authenticated."
                ));
    }
}
