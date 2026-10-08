package com.dazzle.asklepios.web.rest.vm.availabilityGenerationBatch;

public record CancelUpcomingAvailableSlotsResponseVM(
        Long availabilityGenerationBatchId,
        int cancelledCount,
        String cancelReason
) {
}
