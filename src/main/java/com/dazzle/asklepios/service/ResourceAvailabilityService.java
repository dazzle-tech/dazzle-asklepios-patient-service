package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ResourceAvailabilitySlice;
import com.dazzle.asklepios.repository.ResourceAvailabilitySliceRepository;
import com.dazzle.asklepios.web.rest.dto.AvailabilityEntryDTO;
import com.dazzle.asklepios.web.rest.dto.ResourceAvailabilityDTO;
import com.dazzle.asklepios.web.rest.dto.ResourceAvailabilityRequestDTO;
import com.dazzle.asklepios.web.rest.dto.TimeSliceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ResourceAvailabilityService {

    private final ResourceAvailabilitySliceRepository availabilitySliceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ResourceService resourceService;

    public ResourceAvailabilityService(
            ResourceAvailabilitySliceRepository availabilitySliceRepository,
            JdbcTemplate jdbcTemplate,
            ResourceService resourceService) {
        this.availabilitySliceRepository = availabilitySliceRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.resourceService = resourceService;
    }

    public List<ResourceAvailabilityDTO.Availability> getRealAvailability(String resourceKey) {
        List<ResourceAvailabilityDTO.Availability> slots = new ArrayList<>();

        String query = "SELECT day_of_week, start_time_minutes, end_time_minutes " +
                "FROM ap_resource_availability_slice " +
                "WHERE resource_key = ? AND is_valid = true AND isbreak = false AND deleted_at IS NULL " +
                "ORDER BY CAST(day_of_week AS INTEGER), CAST(start_time_minutes AS INTEGER)";

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(query, resourceKey);

            for (Map<String, Object> row : result) {
                int dayOfWeek = Integer.parseInt(String.valueOf(row.get("day_of_week")));
                int startMinutes = Integer.parseInt(String.valueOf(row.get("start_time_minutes")));
                int endMinutes = Integer.parseInt(String.valueOf(row.get("end_time_minutes")));

                ResourceAvailabilityDTO.Availability slot = new ResourceAvailabilityDTO.Availability();
                slot.setDayOfWeek(dayOfWeek);
                slot.setStartHour(startMinutes / 60);
                slot.setStartMinute(startMinutes % 60);
                slot.setEndHour(endMinutes / 60);
                slot.setEndMinute(endMinutes % 60);

                slots.add(slot);
            }

        } catch (Exception e) {
            log.error("Failed to fetch availability for resource {}", resourceKey, e);
        }

        return slots;
    }

    public List<ResourceAvailabilityDTO.Availability> buildAvailability(List<ResourceAvailabilityDTO.Availability> slots) {
        List<ResourceAvailabilityDTO.Availability> aggregatedSlots = new ArrayList<>();

        if (slots.isEmpty()) {
            return aggregatedSlots;
        }

        Map<Integer, List<ResourceAvailabilityDTO.Availability>> groupedByDay =
                slots.stream().collect(Collectors.groupingBy(ResourceAvailabilityDTO.Availability::getDayOfWeek));

        for (Map.Entry<Integer, List<ResourceAvailabilityDTO.Availability>> entry : groupedByDay.entrySet()) {
            int day = entry.getKey();
            List<ResourceAvailabilityDTO.Availability> daySlots = entry.getValue();

            daySlots.sort(Comparator.comparingInt(s -> s.getStartHour() * 60 + s.getStartMinute()));
            int currentStartHour = daySlots.get(0).getStartHour();
            int currentStartMinute = daySlots.get(0).getStartMinute();
            int currentEndHour = daySlots.get(0).getEndHour();
            int currentEndMinute = daySlots.get(0).getEndMinute();

            for (int i = 1; i < daySlots.size(); i++) {
                ResourceAvailabilityDTO.Availability slot = daySlots.get(i);
                int slotStartMinutes = slot.getStartHour() * 60 + slot.getStartMinute();
                int currentEndMinutes = currentEndHour * 60 + currentEndMinute;

                if (slotStartMinutes <= currentEndMinutes) {
                    int slotEndMinutes = slot.getEndHour() * 60 + slot.getEndMinute();
                    if (slotEndMinutes > currentEndMinutes) {
                        currentEndHour = slot.getEndHour();
                        currentEndMinute = slot.getEndMinute();
                    }
                } else {
                    ResourceAvailabilityDTO.Availability agg = new ResourceAvailabilityDTO.Availability();
                    agg.setDayOfWeek(day);
                    agg.setStartHour(currentStartHour);
                    agg.setStartMinute(currentStartMinute);
                    agg.setEndHour(currentEndHour);
                    agg.setEndMinute(currentEndMinute);
                    aggregatedSlots.add(agg);

                    currentStartHour = slot.getStartHour();
                    currentStartMinute = slot.getStartMinute();
                    currentEndHour = slot.getEndHour();
                    currentEndMinute = slot.getEndMinute();
                }
            }
            ResourceAvailabilityDTO.Availability agg = new ResourceAvailabilityDTO.Availability();
            agg.setDayOfWeek(day);
            agg.setStartHour(currentStartHour);
            agg.setStartMinute(currentStartMinute);
            agg.setEndHour(currentEndHour);
            agg.setEndMinute(currentEndMinute);
            aggregatedSlots.add(agg);
        }

        return aggregatedSlots;
    }

    public List<ResourceAvailabilityDTO.RowAvailabilitySlice> getAvailabilitySlices(String resourceKey) {
        List<ResourceAvailabilitySlice> rawSlices = availabilitySliceRepository.findActiveSlicesByResourceKey(resourceKey);

        return rawSlices.stream()
                .map(raw -> {
                    ResourceAvailabilityDTO.RowAvailabilitySlice slice = new ResourceAvailabilityDTO.RowAvailabilitySlice();
                    slice.setKey(raw.getKey());
                    slice.setDayOfWeek(raw.getDayOfWeek());
                    
                    int startMinutes = Integer.parseInt(raw.getStartTimeMinutes());
                    int endMinutes = Integer.parseInt(raw.getEndTimeMinutes());
                    slice.setStartHour(startMinutes / 60);
                    slice.setStartMinute(startMinutes % 60);
                    slice.setEndHour(endMinutes / 60);
                    slice.setEndMinute(endMinutes % 60);
                    
                    slice.setBreak(raw.getIsBreak() != null ? raw.getIsBreak() : false);
                    slice.setFacilityKey(raw.getFacilityKey());
                    return slice;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void saveFullAvailability(ResourceAvailabilityRequestDTO requestDTO) {
        String facilityKey = requestDTO.getFacility();
        String resourceKey = requestDTO.getResource();
        String departmentKey = "default_department";

        List<ResourceAvailabilitySlice> existingActiveSlices;
        if (facilityKey == null || "null".equalsIgnoreCase(facilityKey)) {
            existingActiveSlices = availabilitySliceRepository.findByResourceKeyAndIsValidTrueAndDeletedAtIsNull(resourceKey);
        } else {
            existingActiveSlices = availabilitySliceRepository.findByResourceKeyAndFacilityKeyAndIsValidTrueAndDeletedAtIsNull(resourceKey, facilityKey);
        }

        Map<String, ResourceAvailabilitySlice> existingSlicesMap = existingActiveSlices.stream()
                .collect(Collectors.toMap(
                        slice -> slice.getDayOfWeek() + "_" + slice.getStartTimeMinutes() + "_" + slice.getEndTimeMinutes() + "_" + slice.getIsBreak(),
                        slice -> slice
                ));

        Set<String> newSlicesIdentifiers = new HashSet<>();
        List<String> retainedSliceKeys = new ArrayList<>();

        if (requestDTO.getAvailability() != null && !requestDTO.getAvailability().isEmpty()) {
            for (AvailabilityEntryDTO newDayEntry : requestDTO.getAvailability()) {
                String dayOfWeek = newDayEntry.getDay();
                List<TimeSliceDTO> newSlicesForDay = newDayEntry.getSlices();

                if (newSlicesForDay != null && !newSlicesForDay.isEmpty()) {
                    newSlicesForDay.sort(Comparator.comparingLong(TimeSliceDTO::getStartTimeMinutes));

                    List<TimeSliceDTO> currentDayProcessedSlices = new ArrayList<>();

                    for (TimeSliceDTO newSliceDTO : newSlicesForDay) {
                        long startTimeMinutes = newSliceDTO.getStartTimeMinutes();
                        long endTimeMinutes = newSliceDTO.getEndTimeMinutes();
                        boolean isBreak = newSliceDTO.isBreak();
                        long sliceDurationMinutes = endTimeMinutes - startTimeMinutes;

                        if (startTimeMinutes >= endTimeMinutes || sliceDurationMinutes <= 0) {
                            throw new IllegalArgumentException(
                                    String.format("Invalid time slice: Start time must be before end time or duration invalid for %d-%d on day %s.",
                                            startTimeMinutes, endTimeMinutes, dayOfWeek)
                            );
                        }

                        boolean overlaps = currentDayProcessedSlices.stream().anyMatch(existing -> {
                            long existingStart = existing.getStartTimeMinutes();
                            long existingEnd = existing.getEndTimeMinutes();

                            return (startTimeMinutes >= existingStart && startTimeMinutes < existingEnd) ||
                                    (endTimeMinutes > existingStart && endTimeMinutes <= existingEnd) ||
                                    (startTimeMinutes <= existingStart && endTimeMinutes >= existingEnd);
                        });

                        if (overlaps) {
                            throw new IllegalArgumentException(
                                    String.format("Time slice %d-%d overlaps with another slice provided in this request for day %s for resource %s.",
                                            startTimeMinutes, endTimeMinutes, dayOfWeek, resourceKey)
                            );
                        }
                        currentDayProcessedSlices.add(newSliceDTO);

                        String currentNewSliceIdentifier = dayOfWeek + "_" + startTimeMinutes + "_" + endTimeMinutes + "_" + isBreak;
                        newSlicesIdentifiers.add(currentNewSliceIdentifier);

                        ResourceAvailabilitySlice existingSlice = existingSlicesMap.get(currentNewSliceIdentifier);

                        if (existingSlice != null) {
                            existingSlicesMap.remove(currentNewSliceIdentifier);
                        } else {
                            ResourceAvailabilitySlice record = ResourceAvailabilitySlice.builder()
                                    .key(UUID.randomUUID().toString())
                                    .resourceKey(resourceKey)
                                    .facilityKey(facilityKey)
                                    .departmentKey(departmentKey)
                                    .dayOfWeek(dayOfWeek)
                                    .startTimeMinutes(String.valueOf(startTimeMinutes))
                                    .endTimeMinutes(String.valueOf(endTimeMinutes))
                                    .sliceDurationMinutes(String.valueOf(sliceDurationMinutes))
                                    .isBlocked("N")
                                    .isBreak(isBreak)
                                    .createdAt(new BigDecimal(System.currentTimeMillis()))
                                    .createdBy("system")
                                    .isValid(true)
                                    .deletedAt(null)
                                    .build();

                            availabilitySliceRepository.save(record);
                        }
                    }
                }
            }
        }

        List<ResourceAvailabilitySlice> slicesToSoftDelete = new ArrayList<>();
        for (ResourceAvailabilitySlice slice : existingSlicesMap.values()) {
            boolean isBooked = false;
            if (isBooked) {
                retainedSliceKeys.add(slice.getKey());
            } else {
                slicesToSoftDelete.add(slice);
            }
        }

        if (!slicesToSoftDelete.isEmpty()) {
            for (ResourceAvailabilitySlice slice : slicesToSoftDelete) {
                slice.setIsValid(false);
                slice.setDeletedAt(new BigDecimal(System.currentTimeMillis()));
                slice.setDeletedBy("system");
                availabilitySliceRepository.save(slice);
            }
        }
    }

    public String getFacilityKeyFromSlices(String resourceKey) {
        List<ResourceAvailabilitySlice> rawSlices = availabilitySliceRepository.findActiveSlicesByResourceKey(resourceKey);
        
        if (rawSlices != null && !rawSlices.isEmpty()) {
            ResourceAvailabilitySlice firstSlice = rawSlices.get(0);
            String facilityKey = firstSlice.getFacilityKey();
            
            if (facilityKey == null || facilityKey.isEmpty()) {
                for (ResourceAvailabilitySlice slice : rawSlices) {
                    if (slice.getFacilityKey() != null && !slice.getFacilityKey().isEmpty()) {
                        facilityKey = slice.getFacilityKey();
                        break;
                    }
                }
            }
            return facilityKey;
        }
        
        return null;
    }
}

