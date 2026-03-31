package com.dazzle.asklepios.service;

import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AvailabilityGenerationBatchService {
    private final AvailabilityGenerationBatchRepository batchRepository;
    public AvailabilityGenerationBatchService(AvailabilityGenerationBatchRepository batchRepository) {
        this.batchRepository = batchRepository;
    }

}
