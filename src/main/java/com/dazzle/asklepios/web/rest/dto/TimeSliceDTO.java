package com.dazzle.asklepios.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TimeSliceDTO {
    private long startTimeMinutes;
    private long endTimeMinutes;
    
    @JsonProperty("isBreak")
    private boolean isBreak;
}

