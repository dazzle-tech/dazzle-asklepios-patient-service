package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.DentalActionSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.DentalPlannedTreatmentStatusLog;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothCdt;
import com.dazzle.asklepios.domain.ToothService;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentHistoryAction;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.domain.enumeration.TreatmentScope;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentStatusLogRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartToothSummaryDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalPlannedTreatmentStatusLogDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentBatchSaveDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentClinicalDetails;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentSaveDTO;
import com.dazzle.asklepios.service.helper.CDTCodeHelper;
import com.dazzle.asklepios.service.helper.DentalActionHelper;
import com.dazzle.asklepios.service.helper.ProcedureHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class DentalPlannedTreatmentService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalPlannedTreatmentService.class);
    private static final String ENTITY = "dentalPlannedTreatment";

    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final DentalChartRepository dentalChartRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final ToothActionRepository toothActionRepository;
    private final ToothCdtRepository toothCdtRepository;
    private final DentalActionHelper dentalActionHelper;
    private final DentalVisitNumberService dentalVisitNumberService;
    private final ServiceHelper serviceHelper;
    private final DentalTreatmentPlanService dentalTreatmentPlanService;
    private final CDTCodeHelper cdtCodeHelper;
    private final ProcedureHelper procedureHelper;
    private final DentalPlannedTreatmentStatusLogRepository dentalPlannedTreatmentStatusLogRepository;

    public DentalPlannedTreatmentService(
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository,
            DentalChartToothRepository dentalChartToothRepository,
            DentalChartRepository dentalChartRepository,
            PatientEncounterRepository patientEncounterRepository,
            ToothServiceRepository toothServiceRepository,
            ToothActionRepository toothActionRepository,
            ToothCdtRepository toothCdtRepository,
            DentalActionHelper dentalActionHelper,
            DentalVisitNumberService dentalVisitNumberService,
            ServiceHelper serviceHelper,
            DentalTreatmentPlanService dentalTreatmentPlanService,
            CDTCodeHelper cdtCodeHelper,
            ProcedureHelper procedureHelper,
            DentalPlannedTreatmentStatusLogRepository dentalPlannedTreatmentStatusLogRepository
    ) {
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.dentalChartRepository = dentalChartRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.toothServiceRepository = toothServiceRepository;
        this.toothActionRepository = toothActionRepository;
        this.toothCdtRepository = toothCdtRepository;
        this.dentalActionHelper = dentalActionHelper;
        this.dentalVisitNumberService = dentalVisitNumberService;
        this.serviceHelper = serviceHelper;
        this.dentalTreatmentPlanService = dentalTreatmentPlanService;
        this.cdtCodeHelper = cdtCodeHelper;
        this.procedureHelper = procedureHelper;
        this.dentalPlannedTreatmentStatusLogRepository = dentalPlannedTreatmentStatusLogRepository;
    }

    @Transactional(readOnly = true)
    public List<DentalPlannedTreatmentStatusLogDTO> listHistoryForPlan(Long planId) {
        dentalTreatmentPlanService.get(planId);
        return dentalPlannedTreatmentStatusLogRepository
                .findByTreatmentPlanIdOrderByChangedAtDescIdDesc(planId)
                .stream()
                .map(DentalPlannedTreatmentStatusLogDTO::from)
                .toList();
    }

    /**
     * Not read-only: may persist Session Numbers onto unnumbered Existing chart treatments once.
     */
    @Transactional
    public DentalTreatmentPlanResponseDTO getTreatmentPlanForEncounter(Long encounterId) {
        PatientEncounter encounter = getEncounter(encounterId);

        List<PlannedTreatmentDTO> draftTreatments = dentalPlannedTreatmentRepository
                .findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(
                        encounter.getPatient().getId(),
                        PlannedTreatmentType.DRAFT
                )
                .stream()
                .map(this::toDTO)
                .toList();

        List<PlannedTreatmentDTO> persistedCurrent = dentalPlannedTreatmentRepository
                .findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(
                        encounterId,
                        PlannedTreatmentType.CURRENT
                )
                .stream()
                .map(this::toDTO)
                .toList();

        Set<Long> linkedToothActionIds = Stream.concat(draftTreatments.stream(), persistedCurrent.stream())
                .map(PlannedTreatmentDTO::toothActionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<PlannedTreatmentDTO> currentTreatments = new ArrayList<>(persistedCurrent);
        currentTreatments.addAll(projectExistingChartTreatments(encounter, linkedToothActionIds));

        return new DentalTreatmentPlanResponseDTO(draftTreatments, currentTreatments);
    }

    /**
     * Distinct session numbers already stored for the patient (plans and chart actions),
     * including other encounters. Does not allocate a new number.
     */
    public List<Integer> listPatientSessionNumbers(Long patientId) {
        if (patientId == null) {
            throw new BadRequestAlertException(
                    "Patient is required",
                    ENTITY,
                    "patientRequired"
            );
        }
        Set<Integer> numbers = new LinkedHashSet<>();
        dentalPlannedTreatmentRepository.findDistinctVisitNumbersByPatientId(patientId).stream()
                .filter(n -> n != null && n > 0)
                .forEach(numbers::add);
        toothActionRepository.findDistinctVisitNumbersByPatientId(patientId).stream()
                .filter(n -> n != null && n > 0)
                .forEach(numbers::add);
        return numbers.stream().sorted().toList();
    }

    /**
     * Marks a PENDING treatment In Progress. Does not create a charge.
     */
    public PlannedTreatmentDTO startPlannedTreatment(Long id, Long executionEncounterId) {
        DentalPlannedTreatment entity = getPlannedTreatment(id);
        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "Cancelled planned treatments cannot be started",
                    ENTITY,
                    "cancelledNotStartable"
            );
        }
        if (entity.getStatus() == PlannedTreatmentStatus.COMPLETED
                || entity.getPatientServiceAndProductId() != null) {
            throw new BadRequestAlertException(
                    "Completed planned treatments cannot be started",
                    ENTITY,
                    "completedNotStartable"
            );
        }
        if (entity.getStatus() == PlannedTreatmentStatus.IN_PROGRESS) {
            return toDTO(entity);
        }
        prepareExecution(entity, executionEncounterId);
        entity.setStatus(PlannedTreatmentStatus.IN_PROGRESS);
        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        recordHistory(saved, PlannedTreatmentHistoryAction.IN_PROGRESS);
        dentalTreatmentPlanService.markInProgressIfAccepted(saved.getTreatmentPlanId());
        return toDTO(saved);
    }

    public List<PlannedTreatmentDTO> listForPlan(Long planId) {
        return dentalPlannedTreatmentRepository.findByTreatmentPlanId(planId).stream()
                .sorted(java.util.Comparator
                        .comparing(DentalPlannedTreatment::getVisitNumber, java.util.Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DentalPlannedTreatment::getId, java.util.Comparator.nullsLast(Long::compareTo)))
                .map(this::toDTO)
                .toList();
    }

    public PlannedTreatmentDTO createPlannedTreatment(PlannedTreatmentSaveDTO dto) {
        ResolvedScope resolved = resolveAndValidateScope(dto, true);
        long quantity = resolveBillableQuantity(resolved.scope(), resolved.coveredToothIds(), dto.quantity());
        ValidatedFinancials financials = resolveFinancialsForSave(
                dto.serviceId(),
                dto.procedureId(),
                dto.price(),
                quantity,
                dto.discount()
        );
        com.dazzle.asklepios.domain.DentalTreatmentSession session = requirePlanSession(
                dto.patientId(),
                dto.treatmentPlanId(),
                dto.treatmentSessionId()
        );

        DentalPlannedTreatment entity = buildPendingEntity(
                dto.patientId(),
                dto.encounterId(),
                dto.type(),
                session.getSessionNumber(),
                dto.cdtCodeId(),
                resolved.toothId(),
                resolved.scope(),
                resolved.coveredToothIds(),
                dto.note(),
                resolved.surface(),
                dto.insurance(),
                dto.serviceId(),
                dto.procedureId(),
                financials.unitPrice(),
                financials.quantity(),
                financials.discountAmount(),
                clinicalFrom(dto)
        );
        entity.setTreatmentPlanId(dto.treatmentPlanId());
        entity.setTreatmentSessionId(session.getId());

        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        recordHistory(saved, PlannedTreatmentHistoryAction.CREATED);
        return toDTO(saved);
    }

    /**
     * Atomic multi-tooth Add for Specific Teeth: one PENDING row per tooth,
     * one shared Session Number. Clinical values are copied onto each row.
     */
    public List<PlannedTreatmentDTO> createPlannedTreatmentsBatch(PlannedTreatmentBatchSaveDTO dto) {
        if (dto.toothIds() == null || dto.toothIds().isEmpty()) {
            throw new BadRequestAlertException(
                    "At least one tooth is required",
                    ENTITY,
                    "toothRequired"
            );
        }

        List<Long> toothIds = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long toothId : dto.toothIds()) {
            if (toothId == null) {
                throw new BadRequestAlertException(
                        "Tooth id cannot be null",
                        ENTITY,
                        "invalidToothId"
                );
            }
            if (!seen.add(toothId)) {
                throw new BadRequestAlertException(
                        "Duplicate tooth ids are not allowed in a batch",
                        ENTITY,
                        "duplicateToothIds"
                );
            }
            toothIds.add(toothId);
        }

        PatientEncounter encounter = assertEncounterOwnership(dto.patientId(), dto.encounterId());
        DentalChart chart = requireChart(encounter.getId());
        assertTeethOnChart(chart, toothIds);

        PlannedTreatmentSaveDTO validationDto = new PlannedTreatmentSaveDTO(
                dto.patientId(),
                dto.encounterId(),
                dto.type(),
                dto.visitNumber(),
                dto.cdtCodeId(),
                toothIds.get(0),
                TreatmentScope.SPECIFIC_TOOTH,
                null,
                dto.note(),
                dto.surface(),
                dto.insurance(),
                dto.serviceId(),
                dto.procedureId(),
                dto.price(),
                dto.quantity(),
                dto.discount(),
                dto.anesthesiaUsed(),
                dto.dose(),
                dto.unit(),
                dto.fillingMaterial(),
                dto.treatmentPlanId(),
                dto.treatmentSessionId(),
                dto.revisionReason()
        );
        validateSaveCommon(validationDto);
        // One commercial snapshot per tooth row — do not multiply discount by batch size.
        ValidatedFinancials financials = resolveFinancialsForSave(
                dto.serviceId(),
                dto.procedureId(),
                dto.price(),
                dto.quantity(),
                dto.discount()
        );

        com.dazzle.asklepios.domain.DentalTreatmentSession session = requirePlanSession(
                dto.patientId(),
                dto.treatmentPlanId(),
                dto.treatmentSessionId()
        );

        PlannedTreatmentClinicalDetails clinical = clinicalFrom(dto);
        List<DentalPlannedTreatment> entities = new ArrayList<>(toothIds.size());
        for (Long toothId : toothIds) {
            DentalPlannedTreatment row = buildPendingEntity(
                    dto.patientId(),
                    dto.encounterId(),
                    dto.type(),
                    session.getSessionNumber(),
                    dto.cdtCodeId(),
                    toothId,
                    TreatmentScope.SPECIFIC_TOOTH,
                    List.of(),
                    dto.note(),
                    dto.surface(),
                    dto.insurance(),
                    dto.serviceId(),
                    dto.procedureId(),
                    financials.unitPrice(),
                    financials.quantity(),
                    financials.discountAmount(),
                    clinical
            );
            row.setTreatmentPlanId(dto.treatmentPlanId());
            row.setTreatmentSessionId(session.getId());
            entities.add(row);
        }

        List<DentalPlannedTreatment> saved = dentalPlannedTreatmentRepository.saveAll(entities);
        saved.forEach(row -> recordHistory(row, PlannedTreatmentHistoryAction.CREATED));
        return saved.stream()
                .map(this::toDTO)
                .toList();
    }

    public PlannedTreatmentDTO updatePlannedTreatment(Long id, PlannedTreatmentSaveDTO dto) {
        DentalPlannedTreatment entity = getPlannedTreatment(id);
        assertPendingMutable(entity);
        ResolvedScope resolved = resolveAndValidateScopeForUpdate(entity, dto);
        long quantity = resolveBillableQuantity(resolved.scope(), resolved.coveredToothIds(), dto.quantity());
        ValidatedFinancials financials = resolveFinancialsForSave(
                dto.serviceId(),
                dto.procedureId(),
                dto.price(),
                quantity,
                dto.discount()
        );

        boolean scopeOrCommercialChanged = commercialChanged(entity, dto, resolved);
        if (scopeOrCommercialChanged && entity.getTreatmentPlanId() != null) {
            dentalTreatmentPlanService.returnToProposedForRevision(
                    entity.getTreatmentPlanId(),
                    dto.revisionReason()
            );
        }

        com.dazzle.asklepios.domain.DentalTreatmentSession session = null;
        if (dto.treatmentPlanId() != null) {
            session = requirePlanSession(dto.patientId(), dto.treatmentPlanId(), dto.treatmentSessionId());
        }

        entity.setPatientId(dto.patientId());
        entity.setType(dto.type());
        if (session != null) {
            entity.setTreatmentPlanId(dto.treatmentPlanId());
            entity.setTreatmentSessionId(session.getId());
            entity.setVisitNumber(session.getSessionNumber());
        }
        entity.setCdtCodeId(dto.cdtCodeId());
        entity.setToothId(resolved.toothId());
        entity.setTreatmentScope(resolved.scope());
        entity.setCoveredToothIds(PlannedTreatmentDTO.encodeCoveredToothIds(resolved.coveredToothIds()));
        entity.setNote(dto.note());
        entity.setSurface(resolved.surface());
        entity.setInsurance(defaultMoney(dto.insurance()));
        entity.setServiceId(dto.serviceId());
        entity.setProcedureId(dto.procedureId());
        entity.setPrice(financials.unitPrice());
        entity.setQuantity(financials.quantity());
        entity.setDiscount(financials.discountAmount());
        entity.setClinicalDetails(clinicalFrom(dto).toJson());

        return toDTO(dentalPlannedTreatmentRepository.save(entity));
    }

    public PlannedTreatmentDTO cancelPlannedTreatment(Long id, String cancellationReason) {
        DentalPlannedTreatment entity = getPlannedTreatment(id);

        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "Planned treatment is already cancelled",
                    ENTITY,
                    "alreadyCancelled"
            );
        }
        if (entity.getStatus() == PlannedTreatmentStatus.COMPLETED
                || entity.getPatientServiceAndProductId() != null) {
            throw new BadRequestAlertException(
                    "Completed planned treatments cannot be cancelled",
                    ENTITY,
                    "completedNotCancellable"
            );
        }
        if (cancellationReason == null || cancellationReason.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required",
                    ENTITY,
                    "cancellationReasonRequired"
            );
        }

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        entity.setCancelled(true);
        entity.setCancelledBy(currentUser);
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(cancellationReason);

        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        recordHistory(saved, PlannedTreatmentHistoryAction.CANCELLED);
        dentalTreatmentPlanService.evaluateCompletion(saved.getTreatmentPlanId());
        return toDTO(saved);
    }

    /**
     * Soft-cancels an open planned treatment from plan/chart cascade and writes CANCELLED history.
     * Completed rows are left untouched.
     */
    public void cascadeCancelOpen(DentalPlannedTreatment entity, String cancellationReason, String user) {
        if (entity == null || entity.isCancelled()) {
            return;
        }
        if (entity.getStatus() == PlannedTreatmentStatus.COMPLETED
                || entity.getPatientServiceAndProductId() != null) {
            return;
        }
        Instant now = Instant.now();
        entity.setCancelled(true);
        entity.setCancelledBy(user);
        entity.setCancelledDate(now);
        entity.setCancellationReason(cancellationReason);
        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        recordHistory(saved, PlannedTreatmentHistoryAction.CANCELLED);
    }

    public void recordCreatedHistory(DentalPlannedTreatment entity) {
        recordHistory(entity, PlannedTreatmentHistoryAction.CREATED);
    }

    /**
     * Completes a PENDING/IN_PROGRESS planned treatment clinically.
     * Persists the planned price/quantity/discount snapshot on the treatment row.
     * Charge creation / billing-engine integration is deferred.
     */
    public PlannedTreatmentDTO completePlannedTreatment(Long id, Long executionEncounterId) {
        DentalPlannedTreatment entity = getPlannedTreatment(id);

        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "Cancelled planned treatments cannot be completed",
                    ENTITY,
                    "cancelledNotCompletable"
            );
        }

        if (entity.getStatus() == PlannedTreatmentStatus.COMPLETED
                || entity.getPatientServiceAndProductId() != null) {
            return toDTO(entity);
        }

        if (entity.getStatus() != null
                && entity.getStatus() != PlannedTreatmentStatus.PENDING
                && entity.getStatus() != PlannedTreatmentStatus.IN_PROGRESS) {
            throw new BadRequestAlertException(
                    "Only pending or in-progress treatments can be completed",
                    ENTITY,
                    "statusNotCompletable"
            );
        }

        if (entity.getProcedureId() == null && entity.getServiceId() == null) {
            throw new BadRequestAlertException(
                    "Dental procedure is required to complete the planned treatment",
                    ENTITY,
                    "procedureRequired"
            );
        }

        prepareExecution(entity, executionEncounterId);

        TreatmentScope scope = effectiveScope(entity);
        DentalChart chart = requireChart(entity.getExecutionEncounterId());
        List<DentalChartTooth> coveredTeeth = resolveExecutionTeeth(entity, chart, scope);

        if (scope == TreatmentScope.SPECIFIC_TOOTH && entity.getToothId() == null) {
            throw new BadRequestAlertException(
                    "Tooth is required to complete the planned treatment",
                    ENTITY,
                    "toothRequired"
            );
        }
        if (scope != TreatmentScope.SPECIFIC_TOOTH && coveredTeeth.isEmpty()) {
            throw new BadRequestAlertException(
                    "Covered teeth are required to complete a grouped planned treatment",
                    ENTITY,
                    "coveredTeethRequired"
            );
        }

        BigDecimal snapshotPrice = entity.getPrice();
        if (snapshotPrice == null) {
            if (entity.getProcedureId() != null) {
                ProcedureSetupDTO procedure = procedureHelper.getProcedure(entity.getProcedureId());
                snapshotPrice = procedure.price();
            }
            if (snapshotPrice == null && entity.getServiceId() != null) {
                ServiceSetupDTO setupService = serviceHelper.getService(entity.getServiceId());
                if (setupService.price() == null) {
                    throw new BadRequestAlertException(
                            "Service '" + setupService.name() + "' has no price configured",
                            ENTITY,
                            "priceRequired"
                    );
                }
                snapshotPrice = setupService.price();
            }
        }
        List<Long> coverageIds = coveredTeeth.stream()
                .map(DentalChartTooth::getId)
                .filter(Objects::nonNull)
                .toList();
        long billableQty = resolveBillableQuantity(scope, coverageIds, null);
        ValidatedFinancials financials = resolveFinancialsForSave(
                entity.getServiceId(),
                entity.getProcedureId(),
                snapshotPrice,
                billableQty,
                entity.getDiscount()
        );

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        entity.setStatus(PlannedTreatmentStatus.COMPLETED);
        // Billing charge creation is deferred — do not invent a patientServiceAndProductId.
        entity.setPrice(financials.unitPrice());
        entity.setQuantity(financials.quantity());
        entity.setDiscount(financials.discountAmount());
        entity.setCompletedBy(currentUser);
        entity.setCompletedAt(Instant.now());

        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        recordHistory(saved, PlannedTreatmentHistoryAction.COMPLETED);
        dentalTreatmentPlanService.markInProgressIfAccepted(saved.getTreatmentPlanId());
        dentalTreatmentPlanService.evaluateCompletion(saved.getTreatmentPlanId());
        LOG.info(
                "[DENTAL_PLANNED_TREATMENT] Completed clinically id={} scope={} qty={} (billing deferred)",
                saved.getId(),
                scope,
                financials.quantity()
        );
        return toDTO(saved);
    }

    /**
     * Edits keep the original planning encounter. Teeth selected on a later
     * encounter chart are stored as the original chart's tooth ids, matched by
     * tooth number. Execution teeth stay resolved separately at start/complete.
     */
    private ResolvedScope resolveAndValidateScopeForUpdate(
            DentalPlannedTreatment entity,
            PlannedTreatmentSaveDTO dto
    ) {
        validateSaveCommon(dto);
        if (!Objects.equals(dto.patientId(), entity.getPatientId())) {
            throw new BadRequestAlertException(
                    "A treatment cannot be moved to another patient",
                    ENTITY,
                    "patientChanged"
            );
        }
        DentalChart planningChart = requireChart(entity.getEncounterId());
        TreatmentScope scope = dto.treatmentScope() != null
                ? dto.treatmentScope()
                : TreatmentScope.SPECIFIC_TOOTH;

        if (scope == TreatmentScope.SPECIFIC_TOOTH) {
            if (dto.type() == PlannedTreatmentType.CURRENT && dto.toothId() == null) {
                throw new BadRequestAlertException(
                        "A tooth is required for a CURRENT planned treatment",
                        ENTITY,
                        "toothRequiredForCurrent"
                );
            }
            if (dto.coveredToothIds() != null && !dto.coveredToothIds().isEmpty()) {
                throw new BadRequestAlertException(
                        "Covered tooth ids are not allowed for specific-tooth treatments",
                        ENTITY,
                        "coveredTeethNotAllowed"
                );
            }
            Long planningToothId = dto.toothId() == null
                    ? null
                    : mapToPlanningChart(dto.toothId(), planningChart).getId();
            return new ResolvedScope(scope, planningToothId, List.of(), dto.surface());
        }

        List<Long> expectedIds = resolveChartScopeToothIds(planningChart, scope);
        if (expectedIds.isEmpty()) {
            throw new BadRequestAlertException(
                    "No teeth available on the original planning chart for the selected treatment area",
                    ENTITY,
                    "emptyTreatmentArea"
            );
        }
        List<Long> provided = normalizeIds(dto.coveredToothIds());
        List<Long> covered = provided.isEmpty()
                ? expectedIds
                : provided.stream().map(id -> mapToPlanningChart(id, planningChart).getId()).toList();
        if (covered.size() != expectedIds.size() || !new HashSet<>(covered).equals(new HashSet<>(expectedIds))) {
            throw new BadRequestAlertException(
                    "The selected teeth do not match the original planning chart for this treatment area",
                    ENTITY,
                    "planningCoverageUnmatched"
            );
        }
        return new ResolvedScope(scope, null, covered, null);
    }

    /**
     * Incoming tooth ids may belong to the current encounter chart.
     * The stored reference is always the original planning chart tooth
     * with the same tooth number.
     */
    private DentalChartTooth mapToPlanningChart(Long toothId, DentalChart planningChart) {
        DentalChartTooth source = dentalChartToothRepository.findById(toothId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Dental chart tooth not found: " + toothId,
                        "dentalChartTooth",
                        "notfound"
                ));
        if (Objects.equals(source.getChartId(), planningChart.getId())) {
            return source;
        }
        DentalChart sourceChart = dentalChartRepository.findById(source.getChartId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "The selected tooth is not on a dental chart",
                        ENTITY,
                        "toothChartMissing"
                ));
        Long sourcePatientId = sourceChart.getPatient() != null ? sourceChart.getPatient().getId() : null;
        Long planningPatientId = planningChart.getPatient() != null ? planningChart.getPatient().getId() : null;
        if (sourcePatientId == null || !Objects.equals(sourcePatientId, planningPatientId)) {
            throw new BadRequestAlertException(
                    "The selected tooth does not belong to this patient",
                    ENTITY,
                    "toothPatientMismatch"
            );
        }
        List<DentalChartTooth> matches = dentalChartToothRepository.findByChartId(planningChart.getId()).stream()
                .filter(tooth -> Objects.equals(tooth.getToothNumber(), source.getToothNumber()))
                .toList();
        String label = displayLabel(source.getToothNumber());
        if (matches.size() != 1) {
            throw new BadRequestAlertException(
                    "Tooth " + label + " is not on the original planning chart. "
                            + "The planning tooth was not changed.",
                    ENTITY,
                    "planningToothUnmatched"
            );
        }
        return matches.get(0);
    }

    private ResolvedScope resolveAndValidateScope(PlannedTreatmentSaveDTO dto, boolean creating) {
        validateSaveCommon(dto);
        PatientEncounter encounter = assertEncounterOwnership(dto.patientId(), dto.encounterId());
        DentalChart chart = requireChart(encounter.getId());

        TreatmentScope scope = dto.treatmentScope() != null
                ? dto.treatmentScope()
                : TreatmentScope.SPECIFIC_TOOTH;

        if (scope == TreatmentScope.SPECIFIC_TOOTH) {
            if (dto.type() == PlannedTreatmentType.CURRENT && dto.toothId() == null) {
                throw new BadRequestAlertException(
                        "A tooth is required for a CURRENT planned treatment",
                        ENTITY,
                        "toothRequiredForCurrent"
                );
            }
            if (dto.toothId() != null) {
                assertTeethOnChart(chart, List.of(dto.toothId()));
            }
            if (dto.coveredToothIds() != null && !dto.coveredToothIds().isEmpty()) {
                throw new BadRequestAlertException(
                        "Covered tooth ids are not allowed for specific-tooth treatments",
                        ENTITY,
                        "coveredTeethNotAllowed"
                );
            }
            return new ResolvedScope(scope, dto.toothId(), List.of(), dto.surface());
        }

        List<Long> expectedIds = resolveChartScopeToothIds(chart, scope);
        if (expectedIds.isEmpty()) {
            throw new BadRequestAlertException(
                    "No teeth available on the current chart for the selected treatment area",
                    ENTITY,
                    "emptyTreatmentArea"
            );
        }

        List<Long> provided = normalizeIds(dto.coveredToothIds());
        List<Long> covered = provided.isEmpty() ? expectedIds : provided;

        if (covered.size() != expectedIds.size() || !new HashSet<>(covered).equals(new HashSet<>(expectedIds))) {
            throw new BadRequestAlertException(
                    "Covered teeth do not match the selected treatment area for the current chart",
                    ENTITY,
                    "invalidTreatmentAreaCoverage"
            );
        }

        assertTeethOnChart(chart, covered);

        // Grouped rows: no single-tooth linkage, no surface.
        return new ResolvedScope(scope, null, covered, null);
    }

    private void validateSaveCommon(PlannedTreatmentSaveDTO dto) {
        if (dto.visitNumber() != null) {
            dentalVisitNumberService.validatePositive(dto.visitNumber());
        }
        if (dto.cdtCodeId() != null) {
            cdtCodeHelper.validateCDTCodeExists(dto.cdtCodeId());
        }
        if (dto.procedureId() != null) {
            procedureHelper.validateProcedureExists(dto.procedureId());
        }
        if (dto.serviceId() != null) {
            serviceHelper.getService(dto.serviceId());
        }
        if (dto.price() != null && dto.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestAlertException("Unit price cannot be negative", ENTITY, "invalidPrice");
        }
        if (dto.quantity() != null && dto.quantity() <= 0) {
            throw new BadRequestAlertException("Quantity must be a positive number", ENTITY, "invalidQuantity");
        }
        if (dto.discount() != null && dto.discount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestAlertException("Discount amount cannot be negative", ENTITY, "invalidDiscount");
        }
    }

    /**
     * Grouped scopes bill per covered tooth: quantity equals coveredToothIds size.
     * Specific-tooth rows keep the requested quantity (default 1).
     */
    private long resolveBillableQuantity(
            TreatmentScope scope,
            List<Long> coveredToothIds,
            Long requestedQuantity
    ) {
        if (scope != null && scope != TreatmentScope.SPECIFIC_TOOTH) {
            long expected = coveredToothIds == null ? 0L : coveredToothIds.size();
            if (expected <= 0L) {
                throw new BadRequestAlertException(
                        "Quantity requires at least one covered tooth for the selected treatment area",
                        ENTITY,
                        "invalidQuantity"
                );
            }
            if (requestedQuantity != null && requestedQuantity != expected) {
                throw new BadRequestAlertException(
                        "Quantity must equal the number of teeth in the selected treatment coverage ("
                                + expected + ")",
                        ENTITY,
                        "quantityCoverageMismatch"
                );
            }
            return expected;
        }
        return requestedQuantity != null && requestedQuantity > 0 ? requestedQuantity : 1L;
    }

    private ValidatedFinancials resolveFinancialsForSave(
            Long serviceId,
            Long procedureId,
            BigDecimal requestedPrice,
            Long quantity,
            BigDecimal discount
    ) {
        BigDecimal unitPrice = requestedPrice;
        if (unitPrice == null && procedureId != null) {
            ProcedureSetupDTO setup = procedureHelper.getProcedure(procedureId);
            unitPrice = setup.price();
        }
        if (unitPrice == null && serviceId != null) {
            ServiceSetupDTO setup = serviceHelper.getService(serviceId);
            unitPrice = setup.price();
        }
        if (unitPrice == null) {
            throw new BadRequestAlertException("Unit price is required", ENTITY, "priceRequired");
        }
        BigDecimal price = unitPrice.setScale(2, java.math.RoundingMode.HALF_UP);
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestAlertException("Unit price cannot be negative", ENTITY, "invalidPrice");
        }
        long qty = quantity != null && quantity > 0 ? quantity : 1L;
        BigDecimal discountAmount = (discount != null ? discount : BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestAlertException("Discount amount cannot be negative", ENTITY, "invalidDiscount");
        }
        BigDecimal lineGross = price.multiply(BigDecimal.valueOf(qty)).setScale(2, java.math.RoundingMode.HALF_UP);
        if (discountAmount.compareTo(lineGross) > 0) {
            throw new BadRequestAlertException(
                    "Discount amount cannot exceed unit price × quantity",
                    ENTITY,
                    "discountExceedsLine"
            );
        }
        BigDecimal net = lineGross.subtract(discountAmount).setScale(2, java.math.RoundingMode.HALF_UP);
        return new ValidatedFinancials(price, qty, discountAmount, net);
    }

    private record ValidatedFinancials(
            BigDecimal unitPrice,
            long quantity,
            BigDecimal discountAmount,
            BigDecimal netTotal
    ) {}

    private List<Long> resolveChartScopeToothIds(DentalChart chart, TreatmentScope scope) {
        List<DentalChartTooth> teeth = dentalChartToothRepository.findByChartId(chart.getId()).stream()
                .sorted(Comparator.comparingInt(t -> toothIndexFromEnum(t.getToothNumber())))
                .toList();
        if (teeth.isEmpty()) {
            return List.of();
        }
        int mid = teeth.size() / 2;
        List<DentalChartTooth> selected = switch (scope) {
            case UPPER_ARCH -> teeth.subList(0, mid);
            case LOWER_ARCH -> teeth.subList(mid, teeth.size());
            case FULL_MOUTH -> teeth;
            case SPECIFIC_TOOTH -> List.of();
        };
        return selected.stream()
                .map(DentalChartTooth::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private static int toothIndexFromEnum(String toothNumber) {
        if (toothNumber == null || toothNumber.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String remainder = toothNumber.startsWith("Tooth")
                ? toothNumber.substring("Tooth".length())
                : toothNumber;
        if (remainder.length() == 1) {
            char letter = Character.toUpperCase(remainder.charAt(0));
            if (letter >= 'A' && letter <= 'T') {
                return (letter - 'A') + 1;
            }
        }
        try {
            return Integer.parseInt(remainder);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static String displayLabel(String toothNumber) {
        if (toothNumber == null) {
            return "";
        }
        return toothNumber.startsWith("Tooth")
                ? toothNumber.substring("Tooth".length())
                : toothNumber;
    }

    private List<DentalChartTooth> resolvePersistedCoverage(
            DentalPlannedTreatment entity,
            DentalChart chart,
            TreatmentScope scope
    ) {
        if (scope == TreatmentScope.SPECIFIC_TOOTH) {
            if (entity.getToothId() == null) {
                return List.of();
            }
            DentalChartTooth tooth = dentalChartToothRepository.findById(entity.getToothId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Dental chart tooth not found: " + entity.getToothId(),
                            "dentalChartTooth",
                            "notfound"
                    ));
            if (!Objects.equals(tooth.getChartId(), chart.getId())) {
                throw new BadRequestAlertException(
                        "Selected tooth does not belong to the current encounter chart",
                        ENTITY,
                        "toothNotOnEncounterChart"
                );
            }
            return List.of(tooth);
        }

        List<Long> coveredIds = PlannedTreatmentDTO.parseCoveredToothIds(entity.getCoveredToothIds());
        if (coveredIds.isEmpty()) {
            coveredIds = resolveChartScopeToothIds(chart, scope);
        }
        assertTeethOnChart(chart, coveredIds);
        return dentalChartToothRepository.findAllById(coveredIds);
    }

    private PatientEncounter assertEncounterOwnership(Long patientId, Long encounterId) {
        PatientEncounter encounter = getEncounter(encounterId);
        if (encounter.getPatient() == null || !Objects.equals(encounter.getPatient().getId(), patientId)) {
            throw new BadRequestAlertException(
                    "Encounter does not belong to the given patient",
                    ENTITY,
                    "encounterPatientMismatch"
            );
        }
        return encounter;
    }

    private DentalChart requireChart(Long encounterId) {
        return dentalChartRepository.findByEncounter_Id(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "No dental chart found for this encounter",
                        ENTITY,
                        "chartRequired"
                ));
    }

    private void assertTeethOnChart(DentalChart chart, List<Long> toothIds) {
        if (toothIds == null || toothIds.isEmpty()) {
            return;
        }
        List<DentalChartTooth> found = dentalChartToothRepository.findAllById(toothIds);
        if (found.size() != new HashSet<>(toothIds).size()) {
            throw new NotFoundAlertException(
                    "One or more dental chart teeth were not found",
                    "dentalChartTooth",
                    "notfound"
            );
        }
        for (DentalChartTooth tooth : found) {
            if (!Objects.equals(tooth.getChartId(), chart.getId())) {
                throw new BadRequestAlertException(
                        "Selected tooth does not belong to the current encounter chart",
                        ENTITY,
                        "toothNotOnEncounterChart"
                );
            }
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> unique = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw new BadRequestAlertException(
                        "Covered tooth id cannot be null",
                        ENTITY,
                        "invalidToothId"
                );
            }
            if (seen.add(id)) {
                unique.add(id);
            }
        }
        return unique;
    }

    private TreatmentScope effectiveScope(DentalPlannedTreatment entity) {
        return entity.getTreatmentScope() != null
                ? entity.getTreatmentScope()
                : TreatmentScope.SPECIFIC_TOOTH;
    }

    private PlannedTreatmentClinicalDetails clinicalFrom(PlannedTreatmentSaveDTO dto) {
        return PlannedTreatmentClinicalDetails.of(
                dto.anesthesiaUsed(),
                dto.dose(),
                dto.unit(),
                dto.fillingMaterial()
        );
    }

    private PlannedTreatmentClinicalDetails clinicalFrom(PlannedTreatmentBatchSaveDTO dto) {
        return PlannedTreatmentClinicalDetails.of(
                dto.anesthesiaUsed(),
                dto.dose(),
                dto.unit(),
                dto.fillingMaterial()
        );
    }

    private void assertPendingMutable(DentalPlannedTreatment entity) {
        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "Cancelled planned treatments cannot be edited",
                    ENTITY,
                    "cancelledNotEditable"
            );
        }
        if (entity.getStatus() == PlannedTreatmentStatus.COMPLETED
                || entity.getPatientServiceAndProductId() != null) {
            throw new BadRequestAlertException(
                    "Completed planned treatments cannot be edited",
                    ENTITY,
                    "completedNotEditable"
            );
        }
        if (entity.getStatus() != null
                && entity.getStatus() != PlannedTreatmentStatus.PENDING
                && entity.getStatus() != PlannedTreatmentStatus.IN_PROGRESS) {
            throw new BadRequestAlertException(
                    "Only pending or in-progress treatments can be edited",
                    ENTITY,
                    "statusNotEditable"
            );
        }
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private DentalPlannedTreatment buildPendingEntity(
            Long patientId,
            Long encounterId,
            PlannedTreatmentType type,
            Integer visitNumber,
            Long cdtCodeId,
            Long toothId,
            TreatmentScope scope,
            List<Long> coveredToothIds,
            String note,
            ToothSurface surface,
            BigDecimal insurance,
            Long serviceId,
            Long procedureId,
            BigDecimal price,
            Long quantity,
            BigDecimal discount,
            PlannedTreatmentClinicalDetails clinical
    ) {
        return DentalPlannedTreatment.builder()
                .patientId(patientId)
                .encounterId(encounterId)
                .type(type)
                .visitNumber(visitNumber)
                .cdtCodeId(cdtCodeId)
                .toothId(toothId)
                .treatmentScope(scope != null ? scope : TreatmentScope.SPECIFIC_TOOTH)
                .coveredToothIds(PlannedTreatmentDTO.encodeCoveredToothIds(coveredToothIds))
                .clinicalDetails(clinical != null ? clinical.toJson() : null)
                .note(note)
                .surface(surface)
                .insurance(defaultMoney(insurance))
                .serviceId(serviceId)
                .procedureId(procedureId)
                .price(defaultMoney(price))
                .quantity(quantity != null && quantity > 0 ? quantity : 1L)
                .discount(defaultMoney(discount))
                .status(PlannedTreatmentStatus.PENDING)
                .patientServiceAndProductId(null)
                .cancelled(false)
                .build();
    }

    /**
     * Project chart-only Existing treatments into the unified Treatment Plan table.
     * Documentation rows only — no planned-treatment persistence and no charges.
     */
    private List<PlannedTreatmentDTO> projectExistingChartTreatments(
            PatientEncounter encounter,
            Set<Long> linkedToothActionIds
    ) {
        Optional<DentalChart> chartOpt = dentalChartRepository.findByEncounter_Id(encounter.getId());
        if (chartOpt.isEmpty()) {
            return List.of();
        }

        List<DentalChartTooth> teeth = dentalChartToothRepository.findByChartId(chartOpt.get().getId());
        if (teeth.isEmpty()) {
            return List.of();
        }

        List<Long> toothIds = teeth.stream().map(DentalChartTooth::getId).toList();
        List<ToothAction> actions = toothActionRepository.findByToothIdInAndCancelledFalse(toothIds);
        if (actions.isEmpty()) {
            return List.of();
        }

        var toothById = teeth.stream().collect(Collectors.toMap(DentalChartTooth::getId, t -> t));
        List<ToothAction> existingTreatments = new ArrayList<>();
        List<DentalActionSetupDTO> existingTreatmentDefs = new ArrayList<>();

        for (ToothAction action : actions) {
            if (!action.isExisting()) {
                continue;
            }
            if (action.getId() != null && linkedToothActionIds.contains(action.getId())) {
                continue;
            }

            DentalActionSetupDTO dentalAction;
            try {
                dentalAction = dentalActionHelper.getDentalAction(action.getDentalActionId());
            } catch (RuntimeException ex) {
                LOG.warn(
                        "Skipping existing tooth action {}: dental action {} lookup failed",
                        action.getId(),
                        action.getDentalActionId(),
                        ex
                );
                continue;
            }

            if (dentalAction == null || !"TREATMENT".equalsIgnoreCase(dentalAction.type())) {
                continue;
            }

            if (toothById.get(action.getToothId()) == null) {
                continue;
            }

            existingTreatments.add(action);
            existingTreatmentDefs.add(dentalAction);
        }

        dentalVisitNumberService.ensureExistingActionVisitNumbers(
                encounter.getPatient().getId(),
                existingTreatments
        );

        List<PlannedTreatmentDTO> projected = new ArrayList<>();
        for (int i = 0; i < existingTreatments.size(); i++) {
            ToothAction action = existingTreatments.get(i);
            DentalActionSetupDTO dentalAction = existingTreatmentDefs.get(i);
            DentalChartTooth tooth = toothById.get(action.getToothId());

            Long cdtCodeId = toothCdtRepository.findByToothActionIdAndCancelledFalse(action.getId()).stream()
                    .map(ToothCdt::getCdtCodeId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            Long serviceId = toothServiceRepository.findByToothActionIdAndCancelledFalse(action.getId()).stream()
                    .map(ToothService::getServiceId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            BigDecimal doseValue = null;
            if (action.getDose() != null && !action.getDose().isBlank()) {
                try {
                    doseValue = new BigDecimal(action.getDose().trim());
                } catch (NumberFormatException ignored) {
                    doseValue = null;
                }
            }

            projected.add(new PlannedTreatmentDTO(
                    null,
                    encounter.getPatient().getId(),
                    encounter.getId(),
                    PlannedTreatmentType.CURRENT,
                    action.getVisitNumber(),
                    cdtCodeId,
                    tooth.getId(),
                    TreatmentScope.SPECIFIC_TOOTH,
                    List.of(),
                    action.getNote(),
                    action.getSurface(),
                    null,
                    serviceId,
                    null,
                    null,
                    null,
                    null,
                    PlannedTreatmentStatus.COMPLETED,
                    null,
                    null,
                    action.getId(),
                    false,
                    null,
                    null,
                    DentalChartToothSummaryDTO.fromEntity(tooth),
                    true,
                    dentalAction.description(),
                    action.getAnesthesiaUsed(),
                    doseValue,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }

        return projected;
    }

    private PlannedTreatmentDTO toDTO(DentalPlannedTreatment treatment) {
        DentalChartToothSummaryDTO toothSummary = null;
        if (treatment.getToothId() != null) {
            toothSummary = dentalChartToothRepository.findById(treatment.getToothId())
                    .map(DentalChartToothSummaryDTO::fromEntity)
                    .orElse(null);
        }
        return PlannedTreatmentDTO.fromEntity(treatment, toothSummary);
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "encounterNotFound",
                        ENTITY,
                        "Encounter not found with id " + encounterId
                ));
    }

    private com.dazzle.asklepios.domain.DentalTreatmentSession requirePlanSession(
            Long patientId,
            Long planId,
            Long sessionId
    ) {
        if (planId == null) {
            throw new BadRequestAlertException(
                    "A treatment plan is required",
                    ENTITY,
                    "planRequired"
            );
        }
        dentalTreatmentPlanService.assertPlanForNewTreatment(planId, patientId);
        return dentalTreatmentPlanService.resolveSession(planId, sessionId, sessionId == null);
    }

    private void prepareExecution(DentalPlannedTreatment entity, Long executionEncounterId) {
        if (entity.getTreatmentPlanId() == null) {
            throw new BadRequestAlertException(
                    "This treatment is not linked to a treatment plan",
                    ENTITY,
                    "planRequired"
            );
        }
        Long encounterId = executionEncounterId != null ? executionEncounterId : entity.getEncounterId();
        dentalTreatmentPlanService.requireExecutable(entity.getTreatmentPlanId(), entity.getPatientId());
        PatientEncounter executionEncounter =
                dentalTreatmentPlanService.requireEncounterForPatient(encounterId, entity.getPatientId());
        DentalChart chart = requireChart(encounterId);
        resolveExecutionTeeth(entity, chart, effectiveScope(entity));
        if (entity.getExecutionEncounterId() == null) {
            entity.setExecutionEncounterId(encounterId);
        }
    }

    /**
     * Planned tooth IDs stay as originally recorded. Execution uses the current
     * chart tooth with the same tooth number. A missing match is an error.
     */
    private List<DentalChartTooth> resolveExecutionTeeth(
            DentalPlannedTreatment entity,
            DentalChart executionChart,
            TreatmentScope scope
    ) {
        List<DentalChartTooth> planned = resolvePersistedCoverage(
                entity,
                requireChart(entity.getEncounterId()),
                scope
        );
        if (Objects.equals(executionChart.getId(), requireChart(entity.getEncounterId()).getId())) {
            return planned;
        }
        List<DentalChartTooth> currentTeeth = dentalChartToothRepository.findByChartId(executionChart.getId());
        List<DentalChartTooth> resolved = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (DentalChartTooth plannedTooth : planned) {
            DentalChartTooth match = currentTeeth.stream()
                    .filter(t -> Objects.equals(t.getToothNumber(), plannedTooth.getToothNumber()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                missing.add(displayLabel(plannedTooth.getToothNumber()));
            } else {
                resolved.add(match);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestAlertException(
                    "These planned teeth are not on the current chart: " + String.join(", ", missing)
                            + ". Open the chart for this encounter before continuing the plan.",
                    ENTITY,
                    "executionToothUnmatched"
            );
        }
        return resolved;
    }

    private boolean commercialChanged(
            DentalPlannedTreatment entity,
            PlannedTreatmentSaveDTO dto,
            ResolvedScope resolved
    ) {
        TreatmentScope currentScope = effectiveScope(entity);
        if (currentScope != resolved.scope()) {
            return true;
        }
        if (!Objects.equals(entity.getToothId(), resolved.toothId())) {
            return true;
        }
        if (!Objects.equals(entity.getSurface(), resolved.surface())) {
            return true;
        }
        if (!Objects.equals(entity.getCdtCodeId(), dto.cdtCodeId())) {
            return true;
        }
        if (!Objects.equals(entity.getServiceId(), dto.serviceId())) {
            return true;
        }
        if (!Objects.equals(entity.getProcedureId(), dto.procedureId())) {
            return true;
        }
        // Null commercial fields mean "leave unchanged" — do not coerce to zero.
        if (dto.price() != null
                && defaultMoney(entity.getPrice()).compareTo(defaultMoney(dto.price())) != 0) {
            return true;
        }
        if (dto.quantity() != null) {
            long currentQty = entity.getQuantity() != null ? entity.getQuantity() : 1L;
            long nextQty = dto.quantity() > 0 ? dto.quantity() : 1L;
            if (currentQty != nextQty) {
                return true;
            }
        }
        if (dto.discount() != null
                && defaultMoney(entity.getDiscount()).compareTo(defaultMoney(dto.discount())) != 0) {
            return true;
        }
        PlannedTreatmentClinicalDetails current =
                PlannedTreatmentClinicalDetails.fromJson(entity.getClinicalDetails());
        PlannedTreatmentClinicalDetails next = clinicalFrom(dto);
        return !Objects.equals(current.anesthesiaUsed(), next.anesthesiaUsed())
                || !moneyEquals(current.dose(), next.dose())
                || !Objects.equals(current.unit(), next.unit())
                || !Objects.equals(current.fillingMaterial(), next.fillingMaterial())
                || !sameIds(PlannedTreatmentDTO.parseCoveredToothIds(entity.getCoveredToothIds()), resolved.coveredToothIds());
    }

    private boolean moneyEquals(java.math.BigDecimal left, java.math.BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private boolean sameIds(List<Long> left, List<Long> right) {
        Set<Long> a = new HashSet<>(left == null ? List.of() : left);
        Set<Long> b = new HashSet<>(right == null ? List.of() : right);
        return a.equals(b);
    }

    private DentalPlannedTreatment getPlannedTreatment(Long id) {
        return dentalPlannedTreatmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Planned treatment not found: " + id,
                        ENTITY,
                        "notfound"
                ));
    }

    private void recordHistory(DentalPlannedTreatment entity, PlannedTreatmentHistoryAction action) {
        if (entity.getTreatmentPlanId() == null || entity.getId() == null) {
            return;
        }
        String toothLabel = null;
        if (entity.getToothId() != null) {
            toothLabel = dentalChartToothRepository.findById(entity.getToothId())
                    .map(t -> displayLabel(t.getToothNumber()))
                    .orElse(null);
        }
        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));
        dentalPlannedTreatmentStatusLogRepository.save(DentalPlannedTreatmentStatusLog.builder()
                .plannedTreatmentId(entity.getId())
                .treatmentPlanId(entity.getTreatmentPlanId())
                .toothId(entity.getToothId())
                .toothLabel(toothLabel)
                .action(action)
                .changedBy(currentUser)
                .changedAt(Instant.now())
                .build());
    }

    private record ResolvedScope(
            TreatmentScope scope,
            Long toothId,
            List<Long> coveredToothIds,
            ToothSurface surface
    ) {}
}
