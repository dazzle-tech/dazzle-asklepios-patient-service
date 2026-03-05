package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestCollectedSample;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestCollectedSampleRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticTestRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleBulkSameDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestCollectedSampleDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.collectedsamples.DiagnosticOrderTestSampleLabelDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DiagnosticOrderTestCollectedSampleService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestCollectedSampleService.class);

    private final DiagnosticOrderTestCollectedSampleRepository repository;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;
    private final DiagnosticOrderTestRepository orderTestRepository;
    private final DiagnosticOrderTestCollectedSampleRepository sampleRepository;
    private final DiagnosticOrderRepository orderRepository;
    private final PatientRepository patientRepository;
    private final DiagnosticTestRepository diagnosticTestRepository;
    public DiagnosticOrderTestCollectedSampleService(
            DiagnosticOrderTestCollectedSampleRepository repository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService, DiagnosticOrderTestRepository orderTestRepository, DiagnosticOrderTestCollectedSampleRepository sampleRepository, DiagnosticOrderRepository orderRepository, PatientRepository patientRepository, DiagnosticTestRepository diagnosticTestRepository
    ) {
        this.repository = repository;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.orderTestRepository = orderTestRepository;
        this.sampleRepository = sampleRepository;
        this.orderRepository = orderRepository;
        this.patientRepository = patientRepository;
        this.diagnosticTestRepository = diagnosticTestRepository;
    }

    public DiagnosticOrderTestCollectedSample create(DiagnosticOrderTestCollectedSampleDTO dto) {
        LOG.debug("[CollectedSampleService] CREATE - start. payload={}", dto);

        // Build collected sample entity
        DiagnosticOrderTestCollectedSample s = new DiagnosticOrderTestCollectedSample();
        s.setOrderId(dto.orderId());
        s.setOrderTestId(dto.orderTestId());
        s.setUnit(dto.unit());
        s.setQuantity(dto.quantity());
        s.setCollectedAt(dto.collectedAt());

        // Persist collected sample
        DiagnosticOrderTestCollectedSample saved = repository.save(s);

        // Update test processing status: NEW -> SAMPLE_COLLECTED
        diagnosticOrderTestStatusService.collectSample(dto.orderTestId());

        LOG.debug("[CollectedSampleService] CREATE - done. id={} orderId={} orderTestId={}",
                saved.getId(), saved.getOrderId(), saved.getOrderTestId());
        return saved;
    }

    public List<DiagnosticOrderTestCollectedSample> bulkCreateWithSameDetails(
            DiagnosticOrderTestCollectedSampleBulkSameDTO dto
    ) {
        LOG.debug("[CollectedSampleService] BULK_CREATE_SAME - start. orderId={} orderTestIdsCount={}",
                dto.orderId(), dto.orderTestIds() == null ? 0 : dto.orderTestIds().size());

        // Build entities for all orderTestIds
        List<DiagnosticOrderTestCollectedSample> entities = dto.orderTestIds().stream().map(orderTestId -> {
            DiagnosticOrderTestCollectedSample s = new DiagnosticOrderTestCollectedSample();
            s.setOrderId(dto.orderId());
            s.setOrderTestId(orderTestId);
            s.setUnit(dto.unit());
            s.setQuantity(dto.quantity());
            s.setCollectedAt(dto.collectedAt());
            return s;
        }).toList();

        // Persist all collected samples
        List<DiagnosticOrderTestCollectedSample> saved = repository.saveAll(entities);

        // Update processing status for each test
        dto.orderTestIds().forEach(diagnosticOrderTestStatusService::collectSample);

        LOG.debug("[CollectedSampleService] BULK_CREATE_SAME - done. savedCount={} orderId={}",
                saved.size(), dto.orderId());
        return saved;
    }

    public void delete(Long id) {
        LOG.debug("[CollectedSampleService] DELETE - start. id={}", id);
        repository.deleteById(id);
        LOG.debug("[CollectedSampleService] DELETE - done. id={}", id);
    }

   //TODO move this logic to analytic service
   public DiagnosticOrderTestSampleLabelDTO getSampleLabel(Long orderTestId) {

       LOG.debug("[SampleLabelService] GET_SAMPLE_LABEL - start. orderTestId={}", orderTestId);

       var orderTest = orderTestRepository.findById(orderTestId)
               .orElseThrow(() -> new BadRequestAlertException(
                       "notfound",
                       "diagnostic_order_tests",
                       "DiagnosticOrderTest not found with id " + orderTestId
               ));

       var lastSample = sampleRepository
               .findTopByOrderTestIdOrderByCreatedDateDescIdDesc(orderTestId)
               .orElseThrow(() -> new BadRequestAlertException(
                       "no_sample",
                       "diagnostic_order_test_collected_samples",
                       "No collected sample found for orderTestId " + orderTestId
               ));

       Long orderId = orderTest.getOrderId();
       if (orderId == null) {
           throw new BadRequestAlertException(
                   "invalid_order",
                   "diagnostic_order_tests",
                   "OrderId is null for orderTestId " + orderTestId
           );
       }

       var order = orderRepository.findById(orderId)
               .orElseThrow(() -> new BadRequestAlertException(
                       "notfound",
                       "diagnostic_orders",
                       "Order not found with id " + orderId
               ));

       var patient = patientRepository.findById(order.getPatientId())
               .orElseThrow(() -> new BadRequestAlertException(
                       "notfound",
                       "patients",
                       "Patient not found with id " + order.getPatientId()
               ));

       var test = diagnosticTestRepository.findById(orderTest.getTestId())
               .orElseThrow(() -> new BadRequestAlertException(
                       "notfound",
                       "diagnostic_tests",
                       "Diagnostic test not found with id " + orderTest.getTestId()
               ));

       String patientName = (patient.getFirstName() + " " + patient.getLastName()).trim();
       String mrn = patient.getMedicalRecordNumber();

       String facilityName = "Asklepios Medical Center";

       LOG.debug(
               "[SampleLabelService] GET_SAMPLE_LABEL - data prepared. orderTestId={} patient={} test={}",
               orderTestId,
               patientName,
               test.getName()
       );

       return new DiagnosticOrderTestSampleLabelDTO(
               orderTestId,
               patientName,
               facilityName,
               mrn,
               test.getName(),
               lastSample.getCollectedAt(),
               lastSample.getQuantity(),
               lastSample.getUnit()
       );
   }
}
