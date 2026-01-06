package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DiagnosticOrderStatusService {

    private final DiagnosticOrderTestRepository testRepository;
    private final DiagnosticOrderRepository orderRepository;

    public DiagnosticOrderStatusService(DiagnosticOrderTestRepository testRepository,
                                        DiagnosticOrderRepository orderRepository) {
        this.testRepository = testRepository;
        this.orderRepository = orderRepository;
    }

    public void recomputeLabRadStatuses(Long orderId) {
        DiagnosticOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrder not found with id " + orderId,
                        "diagnostic_orders",
                        "notfound"
                ));

        DiagnosticStatus labStatus = resolveGroupStatus(orderId, TestType.LABORATORY);
        DiagnosticStatus radStatus = resolveGroupStatus(orderId, TestType.RADIOLOGY);

        order.setLabStatus(labStatus);
        order.setRadStatus(radStatus);

        orderRepository.save(order);
    }

    private DiagnosticStatus resolveGroupStatus(Long orderId, TestType type) {
        List<DiagnosticStatus> distinct = testRepository.findDistinctProcessingStatuses(orderId, type);

        if (distinct == null || distinct.isEmpty()) {
            return DiagnosticStatus.NEW;
        }

        if (distinct.size() == 1) {
            return distinct.get(0);
        }

        return DiagnosticStatus.PARTIAL;
    }

}
