package com.dazzle.asklepios.client.setup.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomDTO(
        Long id,
        String name

) {

}
