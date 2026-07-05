package com.dazzle.asklepios.web.rest.vm.appointmentWaitingList;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WaitingListAvailableSlotsByBookingModeVM(
        @JsonProperty("SLOT")
        List<WaitingListAvailableSlotVM> slot,

        @JsonProperty("BUFFER")
        List<WaitingListAvailableSlotVM> buffer
) {}