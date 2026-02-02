package com.dazzle.asklepios.web.rest.dto;

import lombok.Data;
import java.util.List;

@Data
public class ResourceAvailabilityDTO {
    private String key;
    private String resourceKey;
    private String resourceName;
    private String resourceTypeLkey;
    private String facilityKey;

    private List<Availability> availability;

    private List<RowAvailabilitySlice> availabilitySlices;

    private List<EventSlice> eventSlices;

    @Data
    public static class Availability {
        private int dayOfWeek;
        private int startHour;
        private int startMinute;
        private int endHour;
        private int endMinute;
    }

    @Data
    public static class RowAvailabilitySlice {
        private String key;
        private String dayOfWeek;
        private int startHour;
        private int startMinute;
        private int endHour;
        private int endMinute;
        private boolean isBreak;
        private String facilityKey;
    }

    @Data
    public static class EventSlice {
        private String from;       // ISO
        private String to;         // ISO datetime
        private String eventType;  // booking/ leave/ maintenance ..etc
        private String description;
    }
}

