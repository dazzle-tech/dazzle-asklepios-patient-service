package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.ResourceAvailabilityService;
import com.dazzle.asklepios.service.ResourceService;
import com.dazzle.asklepios.web.rest.dto.ResourceAvailabilityDTO;
import com.dazzle.asklepios.web.rest.dto.ResourceAvailabilityRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/appointment")
@Slf4j
public class ResourceAvailabilityController {

    private final ResourceService resourceService;
    private final ResourceAvailabilityService resourceAvailabilityService;

    public ResourceAvailabilityController(
            ResourceService resourceService,
            ResourceAvailabilityService resourceAvailabilityService) {
        this.resourceService = resourceService;
        this.resourceAvailabilityService = resourceAvailabilityService;
    }

    @GetMapping(value = "/resources-with-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resourcesWithAvailability(
            @RequestParam Map<String, String> queryParams,
            @RequestHeader(required = false) String facility_id,
            @RequestHeader(required = false) Integer access_level,
            @RequestHeader(required = false) String lang) {
        try {
            Map<String, Object> response = new HashMap<>();
            List<ResourceAvailabilityDTO> dtoList = new ArrayList<>();

            if ("true".equalsIgnoreCase(queryParams.get("ignore"))) {
                response.put("object", new ArrayList<>());
                response.put("extraNumeric", 0L);
                return ResponseEntity.ok(response);
            }

            String resourceId = queryParams.get("id");

            if (resourceId != null && !resourceId.isEmpty()) {
                Map<String, Object> resource = resourceService.getResource(resourceId);

                if (resource == null) {
                    return ResponseEntity.notFound().build();
                }

                String resourceKey = (String) resource.get("resource_key");
                String resourceType = (String) resource.get("resource_type_lkey");
                
                String resourceName = (String) resource.get("resource_name");
                if (resourceName == null || resourceName.isEmpty()) {
                    resourceName = resourceService.getResourceName(resourceType, resourceKey);
                }

                ResourceAvailabilityDTO dto = new ResourceAvailabilityDTO();
                dto.setKey(resourceKey);
                dto.setResourceKey(resourceKey);
                dto.setResourceName(resourceName);
                dto.setResourceTypeLkey(resourceType);

                List<ResourceAvailabilityDTO.RowAvailabilitySlice> availabilitySlices =
                        resourceAvailabilityService.getAvailabilitySlices(resourceKey);
                dto.setAvailabilitySlices(availabilitySlices);

                if (availabilitySlices != null && !availabilitySlices.isEmpty()) {
                    dto.setFacilityKey(availabilitySlices.get(0).getFacilityKey());
                } else {
                    dto.setFacilityKey((String) resource.get("facility_key"));
                }

                if (dto.getFacilityKey() == null) {
                    String facilityKeyFromSlices = resourceAvailabilityService.getFacilityKeyFromSlices(resourceKey);
                    if (facilityKeyFromSlices != null) {
                        dto.setFacilityKey(facilityKeyFromSlices);
                    }
                }

                List<ResourceAvailabilityDTO.Availability> slots = 
                        resourceAvailabilityService.getRealAvailability(resourceKey);
                List<ResourceAvailabilityDTO.Availability> aggregated = 
                        resourceAvailabilityService.buildAvailability(slots);
                dto.setAvailability(aggregated);

                dtoList.add(dto);
                response.put("object", dtoList);
                response.put("extraNumeric", 1L);
                return ResponseEntity.ok(response);

            } else {
                String whereClause = buildWhereClause(queryParams);
                List<Map<String, Object>> resources = resourceService.getResources(whereClause);
                String whereForTotal = buildWhereClauseForTotal(queryParams);
                Long totalRecord = resourceService.getResourceCount(whereForTotal);

                for (Map<String, Object> resource : resources) {
                    String resourceKey = (String) resource.get("resource_key");
                    String resourceType = (String) resource.get("resource_type_lkey");
                    
                    String resourceName = (String) resource.get("resource_name");
                    if (resourceName == null || resourceName.isEmpty()) {
                        resourceName = resourceService.getResourceName(resourceType, resourceKey);
                    }

                    ResourceAvailabilityDTO dto = new ResourceAvailabilityDTO();
                    dto.setKey(resourceKey);
                    dto.setResourceKey(resourceKey);
                    dto.setResourceName(resourceName);
                    dto.setResourceTypeLkey(resourceType);
                    dto.setFacilityKey((String) resource.get("facility_key"));

                    List<ResourceAvailabilityDTO.RowAvailabilitySlice> availabilitySlices = 
                            resourceAvailabilityService.getAvailabilitySlices(resourceKey);
                    dto.setAvailabilitySlices(availabilitySlices);
                    
                    if (dto.getFacilityKey() == null) {
                        String facilityKeyFromSlices = resourceAvailabilityService.getFacilityKeyFromSlices(resourceKey);
                        if (facilityKeyFromSlices != null) {
                            dto.setFacilityKey(facilityKeyFromSlices);
                        }
                    }

                    List<ResourceAvailabilityDTO.Availability> slots = 
                            resourceAvailabilityService.getRealAvailability(resourceKey);
                    List<ResourceAvailabilityDTO.Availability> aggregated = 
                            resourceAvailabilityService.buildAvailability(slots);
                    dto.setAvailability(aggregated);

                    dtoList.add(dto);
                }

                response.put("object", dtoList);
                response.put("extraNumeric", totalRecord);
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("Exception in /resources-with-availability: ", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    private String buildWhereClause(Map<String, String> queryParams) {
        StringBuilder where = new StringBuilder("1=1");
        
        if (queryParams.containsKey("filters")) {
            String filters = queryParams.get("filters");
            if (filters != null && !filters.isEmpty()) {
                String[] filterSegments = filters.split("_fspr_");
                for (String segment : filterSegments) {
                    String[] parts = segment.split(",");
                    if (parts.length >= 3) {
                        String fieldName = parts[0];
                        String operator = parts[1];
                        String value = parts[2];
                        
                        if (fieldName.equals("resource_type_lkey")) {
                            fieldName = "resource_type";
                        } else if (fieldName.equals("is_valid")) {
                            fieldName = "is_active";
                        } else if (fieldName.equals("key")) {
                            if (value.matches("\\d+")) {
                                fieldName = "id";
                            } else {
                                fieldName = "resource_key";
                            }
                        }
                        
                        if (operator.equals("match")) {
                            where.append(" AND ").append(fieldName).append(" = '").append(value).append("'");
                        } else if (operator.equals("contains")) {
                            where.append(" AND ").append(fieldName).append(" LIKE '%").append(value).append("%'");
                        }
                    }
                }
            }
        }
        
        return where.toString();
    }

    private String buildWhereClauseForTotal(Map<String, String> queryParams) {
        return buildWhereClause(queryParams);
    }
    @GetMapping(value = "/resources-availability-list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resourcesAvailabilityList(
            @RequestParam(required = false) String resource_key,
            @RequestParam(required = false) String facility_id,
            @RequestHeader(required = false) String lang) {
        try {
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> availabilityList = new ArrayList<>();

            if (resource_key == null || resource_key.isEmpty() || "null".equals(resource_key)) {
                response.put("object", availabilityList);
                return ResponseEntity.ok(response);
            }

            List<ResourceAvailabilityDTO.RowAvailabilitySlice> availabilitySlices = 
                    resourceAvailabilityService.getAvailabilitySlices(resource_key);

            if (facility_id != null && !facility_id.isEmpty() && !"null".equals(facility_id)) {
                availabilitySlices = availabilitySlices.stream()
                        .filter(slice -> facility_id.equals(slice.getFacilityKey()))
                        .collect(Collectors.toList());
            }

            for (ResourceAvailabilityDTO.RowAvailabilitySlice slice : availabilitySlices) {
                Map<String, Object> availabilityTime = new HashMap<>();
                availabilityTime.put("key", slice.getKey());
                availabilityTime.put("resourceKey", resource_key);
                availabilityTime.put("facilityKey", slice.getFacilityKey());
                availabilityTime.put("departmentKey", null);
                availabilityTime.put("dayLkey", slice.getDayOfWeek());
                
                int startTimeMinutes = slice.getStartHour() * 60 + slice.getStartMinute();
                int endTimeMinutes = slice.getEndHour() * 60 + slice.getEndMinute();
                
                availabilityTime.put("startTime", startTimeMinutes);
                availabilityTime.put("endTime", endTimeMinutes);
                availabilityTime.put("isHasBreak", slice.isBreak());
                availabilityTime.put("breakFrom", null);
                availabilityTime.put("breakTo", null);
                availabilityTime.put("createdBy", null);
                availabilityTime.put("updatedBy", null);
                availabilityTime.put("deletedBy", null);
                availabilityTime.put("createdAt", null);
                availabilityTime.put("updatedAt", null);
                availabilityTime.put("deletedAt", null);
                availabilityTime.put("isValid", true);
                
                availabilityList.add(availabilityTime);
            }

            response.put("object", availabilityList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Exception in /resources-availability-list: ", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveAvailability(@RequestBody ResourceAvailabilityRequestDTO requestDTO) {
        try {
            if (requestDTO.getResource() == null || requestDTO.getResource().isEmpty()) {
                return ResponseEntity.badRequest().body("Error: Resource key is required");
            }
            
            resourceAvailabilityService.saveFullAvailability(requestDTO);
            return ResponseEntity.ok().body("Availability saved successfully");
        } catch (IllegalArgumentException e) {
            log.error("Validation error saving availability", e);
            return ResponseEntity.badRequest().body("Error saving availability: " + e.getMessage());
        } catch (Exception e) {
            log.error("Exception saving availability", e);
            return ResponseEntity.status(500).body("Error saving availability: " + e.getMessage());
        }
    }
}

