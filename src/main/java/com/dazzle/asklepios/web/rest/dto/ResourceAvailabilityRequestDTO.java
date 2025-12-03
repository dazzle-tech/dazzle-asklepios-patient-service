package com.dazzle.asklepios.web.rest.dto;

import lombok.Data;
import java.util.List;

@Data
public class ResourceAvailabilityRequestDTO {
    private String facility;
    private String resource;
    private List<AvailabilityEntryDTO> availability;
}

