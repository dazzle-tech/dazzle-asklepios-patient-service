package com.dazzle.asklepios.web.rest.vm.appointmentWaitingList;

import java.time.Instant;
import java.util.List;

public record WaitingListAvailableSlotGroupVM(
        Instant groupStart,
        Instant groupEnd,
        Integer totalDurationMinutes,
        List<WaitingListAvailableSlotVM> slots
) {}
