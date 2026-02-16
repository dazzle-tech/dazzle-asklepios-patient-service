package com.dazzle.asklepios.web.rest.dto;

import lombok.Data;
import java.util.List;

@Data
public class AvailabilityEntryDTO {
    private String day;
    private List<TimeSliceDTO> slices;
}

