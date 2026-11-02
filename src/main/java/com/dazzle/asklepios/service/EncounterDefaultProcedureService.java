package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.Priority;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckResult;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultProcedureItem;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Default procedures of a visit: the setup procedures linked to the visit
 * department + specialty. Each one is ordered on the encounter as a regular
 * procedure order (flagged as default) and gets the same billing item a
 * practitioner-ordered procedure gets, so cancellation and pre-authorization
 * keep working through the existing procedure flows.
 */
@Service
@RequiredArgsConstructor
public class EncounterDefaultProcedureService {

    private static final Logger LOG =
            LoggerFactory.getLogger(EncounterDefaultProcedureService.class);

    private static final String ENTITY_NAME =
            "defaultProcedureBilling";

    private final ProcedureClient procedureClient;
    private final PatientProcedureRepository patientProcedureRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final InsurancePriceListCoverageService insurancePriceListCoverageService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final PatientItemPricingApplicationService patientItemPricingApplicationService;

    public record DefaultProcedureOrder(
            PatientProcedure procedure,
            PatientServiceAndProduct billingItem
    ) {
    }

    public InsurancePriceListCoverageCheckResult checkCoverage(
            Long encounterId,
            Long procedureId,
            Currency currency
    ) {
        return insurancePriceListCoverageService.check(
                encounterId,
                BillingItemTypes.PROCEDURE,
                null,
                procedureId,
                null,
                null,
                currency
        );
    }

    /**
     * Orders the default procedure on the encounter (or reuses the active
     * order of the same procedure) and prepares its billing item.
     */
    @Transactional
    public DefaultProcedureOrder findOrCreate(
            PatientEncounter encounter,
            Currency currency,
            PrepareDefaultProcedureItem requestedItem,
            PatientInsurance insurance
    ) {
        ProcedureSetupDTO setupProcedure = loadActiveProcedure(
                requestedItem.procedureId(),
                encounter.getFacilityId()
        );

        PatientProcedure order =
                patientProcedureRepository
                        .findFirstByEncounter_IdAndProcedureIdAndStatusNotOrderByIdAsc(
                                encounter.getId(),
                                setupProcedure.id(),
                                ProcStatus.CANCELLED
                        )
                        .orElseGet(() -> createOrder(encounter, setupProcedure));

        PatientServiceAndProduct billingItem =
                patientServiceAndProductRepository
                        .findByServiceSourceAndSourceIdAndBillingItemType(
                                ServiceSource.PROCEDURE,
                                order.getId(),
                                BillingItemTypes.PROCEDURE
                        )
                        .map(existing -> refreshExistingItem(
                                existing,
                                encounter,
                                currency,
                                insurance
                        ))
                        .orElseGet(() -> createBillingItem(
                                encounter,
                                order,
                                currency,
                                requestedItem,
                                insurance,
                                setupProcedure
                        ));

        return new DefaultProcedureOrder(order, billingItem);
    }

    /**
     * Same status a practitioner-ordered procedure takes while its
     * pre-authorization is with the payer.
     */
    @Transactional
    public void markWaitingPreAuthorization(PatientProcedure order) {
        if (order.getStatus() != ProcStatus.REQUESTED) {
            return;
        }
        order.setStatus(ProcStatus.WAITING_PRE_AUTHORIZATION);
        patientProcedureRepository.saveAndFlush(order);
    }

    /**
     * Unsaved billing item used only to price the procedure before the visit
     * is registered.
     */
    public PatientServiceAndProduct buildPreviewItem(
            PatientEncounter encounter,
            Currency currency,
            PrepareDefaultProcedureItem requestedItem,
            PatientInsurance insurance
    ) {
        ProcedureSetupDTO setupProcedure = loadActiveProcedure(
                requestedItem.procedureId(),
                encounter.getFacilityId()
        );

        return baseBillingItem(encounter, currency, requestedItem, insurance, setupProcedure)
                .sourceId(setupProcedure.id())
                .paymentStatus(PaymentStatus.PENDING)
                .preAuthorizationRequired(Boolean.FALSE)
                .notes("Pricing preview: " + setupProcedure.name())
                .build();
    }

    public ProcedureSetupDTO loadActiveProcedure(
            Long procedureId,
            Long facilityId
    ) {
        ProcedureSetupDTO procedure = fetchProcedure(procedureId);

        if (!Boolean.TRUE.equals(procedure.isActive())) {
            throw new BadRequestAlertException(
                    "Selected setup procedure is inactive.",
                    ENTITY_NAME,
                    "procedure.inactive"
            );
        }

        if (procedure.facilityId() != null
                && !Objects.equals(facilityId, procedure.facilityId())) {
            throw new BadRequestAlertException(
                    "Selected setup procedure does not belong to the encounter facility.",
                    ENTITY_NAME,
                    "procedure.facility.mismatch"
            );
        }

        return procedure;
    }

    private PatientProcedure createOrder(
            PatientEncounter encounter,
            ProcedureSetupDTO setupProcedure
    ) {
        PatientProcedure order = PatientProcedure.builder()
                .procedureId(setupProcedure.id())
                .patient(encounter.getPatient())
                .encounter(encounter)
                .fromFacilityId(encounter.getFacilityId())
                .toFacilityId(encounter.getFacilityId())
                .fromDepartmentId(encounter.getDepartmentId())
                .toDepartmentId(encounter.getDepartmentId())
                .priority(Priority.NORMAL)
                .scheduledDateTime(Instant.now())
                .notes("Encounter default procedure: " + setupProcedure.name())
                .status(ProcStatus.REQUESTED)
                .isDefaultProcedure(Boolean.TRUE)
                .build();

        PatientProcedure saved = patientProcedureRepository.saveAndFlush(order);

        LOG.info(
                "[DEFAULT_PROCEDURE] Ordered default procedure. encounterId={} patientProcedureId={} procedureId={}",
                encounter.getId(),
                saved.getId(),
                setupProcedure.id()
        );

        return saved;
    }

    private PatientServiceAndProduct createBillingItem(
            PatientEncounter encounter,
            PatientProcedure order,
            Currency currency,
            PrepareDefaultProcedureItem requestedItem,
            PatientInsurance insurance,
            ProcedureSetupDTO setupProcedure
    ) {
        PatientServiceAndProduct.PatientServiceAndProductBuilder itemBuilder =
                baseBillingItem(encounter, currency, requestedItem, insurance, setupProcedure)
                        .sourceId(order.getId())
                        .notes("Encounter default procedure: " + setupProcedure.name());

        InsurancePriceListCoverageCheckResult coverageCheck =
                checkCoverage(encounter.getId(), setupProcedure.id(), currency);

        if (coverageCheck.requiresCashConfirmation()) {
            insurancePriceListCoverageService.applyUncoveredCash(itemBuilder, coverageCheck);
        } else {
            preAuthorizationResolutionService.resolveAndPrepareNewItem(
                    itemBuilder,
                    encounter.getId(),
                    BillingItemTypes.PROCEDURE,
                    setupProcedure.id(),
                    null,
                    null,
                    null,
                    insurance != null,
                    currency
            );

            if (insurance != null) {
                preAuthorizationResolutionService.enrichWaseelSbsMapping(
                        itemBuilder,
                        BillingItemTypes.PROCEDURE,
                        setupProcedure.id()
                );
            }
        }

        PatientServiceAndProduct item = itemBuilder.build();
        patientItemPricingApplicationService.applyToItem(item, encounter.getFacilityId());

        return patientServiceAndProductRepository.saveAndFlush(item);
    }

    private PatientServiceAndProduct refreshExistingItem(
            PatientServiceAndProduct existing,
            PatientEncounter encounter,
            Currency currency,
            PatientInsurance insurance
    ) {
        validateExistingItem(existing, encounter, currency, insurance);

        InsurancePriceListCoverageCheckResult coverageCheck =
                checkCoverage(encounter.getId(), existing.getProcedureId(), currency);

        if (coverageCheck.requiresCashConfirmation()) {
            insurancePriceListCoverageService.applyUncoveredCash(existing, coverageCheck);
            return patientServiceAndProductRepository.saveAndFlush(existing);
        }

        if (insurance != null) {
            PreAuthorizationResolutionService.Resolution resolution =
                    preAuthorizationResolutionService.resolve(
                            encounter.getId(),
                            BillingItemTypes.PROCEDURE,
                            existing.getProcedureId(),
                            null,
                            null,
                            null,
                            true,
                            currency
                    );
            preAuthorizationResolutionService.apply(existing, resolution);
            return patientServiceAndProductRepository.saveAndFlush(existing);
        }

        return existing;
    }

    private void validateExistingItem(
            PatientServiceAndProduct existing,
            PatientEncounter encounter,
            Currency currency,
            PatientInsurance insurance
    ) {
        if (!Objects.equals(encounter.getPatient().getId(), existing.getPatientId())) {
            throw new BadRequestAlertException(
                    "Existing default procedure belongs to another patient.",
                    ENTITY_NAME,
                    "existing.patient.mismatch"
            );
        }

        if (currency != existing.getCurrency()) {
            throw new BadRequestAlertException(
                    "Existing default procedure currency does not match facility currency.",
                    ENTITY_NAME,
                    "existing.currency.mismatch"
            );
        }

        Long expectedInsuranceId = insurance == null ? null : insurance.getId();
        if (!Objects.equals(expectedInsuranceId, existing.getPatientInsuranceId())) {
            throw new BadRequestAlertException(
                    "Existing default procedure uses a different coverage selection. Reprice or cancel it before changing coverage.",
                    ENTITY_NAME,
                    "existing.coverage.mismatch"
            );
        }
    }

    private PatientServiceAndProduct.PatientServiceAndProductBuilder baseBillingItem(
            PatientEncounter encounter,
            Currency currency,
            PrepareDefaultProcedureItem requestedItem,
            PatientInsurance insurance,
            ProcedureSetupDTO setupProcedure
    ) {
        return PatientServiceAndProduct.builder()
                .patientId(encounter.getPatient().getId())
                .encounterId(encounter.getId())
                .billingItemType(BillingItemTypes.PROCEDURE)
                .procedureId(setupProcedure.id())
                .serviceSource(ServiceSource.PROCEDURE)
                .quantity(PrepareDefaultProcedureItem.QUANTITY)
                .unitPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .grossAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .patientShareAmount(BigDecimal.ZERO)
                .insuranceShareAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(BigDecimal.ZERO)
                .currency(currency)
                .isBilled(Boolean.FALSE)
                .coverageStatus(
                        insurance == null
                                ? CoverageStatus.NOT_CHECKED
                                : CoverageStatus.COVERED
                )
                .patientInsuranceId(insurance == null ? null : insurance.getId())
                .isExempted(requestedItem.exempted());
    }

    private ProcedureSetupDTO fetchProcedure(Long procedureId) {
        try {
            ProcedureSetupDTO procedure = procedureClient.getProcedure(procedureId);

            if (procedure == null || procedure.id() == null) {
                throw procedureNotFound(procedureId);
            }

            return procedure;
        } catch (FeignException.NotFound exception) {
            throw procedureNotFound(procedureId);
        } catch (FeignException exception) {
            LOG.error(
                    "Setup Service call failed for procedureId={} status={} body={}",
                    procedureId,
                    exception.status(),
                    exception.contentUTF8(),
                    exception
            );
            throw new BadRequestAlertException(
                    "Unable to validate the selected procedure in Setup Service.",
                    ENTITY_NAME,
                    "setup.procedure.call.failed"
            );
        }
    }

    private NotFoundAlertException procedureNotFound(Long procedureId) {
        return new NotFoundAlertException(
                "Setup procedure not found with id " + procedureId,
                ENTITY_NAME,
                "procedure.notfound"
        );
    }
}
