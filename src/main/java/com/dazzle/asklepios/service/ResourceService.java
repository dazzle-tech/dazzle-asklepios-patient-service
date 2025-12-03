package com.dazzle.asklepios.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ResourceService {

    private final JdbcTemplate jdbcTemplate;

    public ResourceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getResourceName(String resourceTypeKey, String key) {
        if (resourceTypeKey == null || resourceTypeKey.isEmpty() || key == null || key.isEmpty()) {
            return null;
        }

        if (resourceTypeKey.equals("PRACTITIONER") || resourceTypeKey.equals("2039534205961578")) {
            try {
                String query = "SELECT first_name, last_name FROM practitioner WHERE id = ? AND (is_active IS NULL OR is_active = true)";
                List<Map<String, Object>> result = jdbcTemplate.queryForList(query, key);
                if (!result.isEmpty()) {
                    Map<String, Object> row = result.get(0);
                    String firstName = (String) row.get("first_name");
                    String lastName = (String) row.get("last_name");
                    if (firstName != null || lastName != null) {
                        String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                        return fullName.trim();
                    }
                }
            } catch (Exception e) {
                try {
                    String altQuery = "SELECT first_name, last_name FROM practitioner WHERE id = ?";
                    List<Map<String, Object>> result = jdbcTemplate.queryForList(altQuery, key);
                    if (!result.isEmpty()) {
                        Map<String, Object> row = result.get(0);
                        String firstName = (String) row.get("first_name");
                        String lastName = (String) row.get("last_name");
                        if (firstName != null || lastName != null) {
                            String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                            return fullName.trim();
                        }
                    }
                } catch (Exception e2) {
                    log.error("Failed to query practitioner", e2);
                }
            }
        } else if (resourceTypeKey.equals("CLINIC") || resourceTypeKey.equals("2039516279378421")) {
            try {
                String query = "SELECT name FROM department WHERE id = ? AND (is_active IS NULL OR is_active = true)";
                List<Map<String, Object>> result = jdbcTemplate.queryForList(query, key);
                if (!result.isEmpty()) {
                    Map<String, Object> row = result.get(0);
                    String name = (String) row.get("name");
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            } catch (Exception e) {
                try {
                    String altQuery = "SELECT name FROM department WHERE id = ?";
                    List<Map<String, Object>> result = jdbcTemplate.queryForList(altQuery, key);
                    if (!result.isEmpty()) {
                        Map<String, Object> row = result.get(0);
                        String name = (String) row.get("name");
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                } catch (Exception e2) {
                    log.error("Failed to query department", e2);
                }
            }
        } else if (resourceTypeKey.equals("MEDICAL_TEST") || resourceTypeKey.equals("2039620472612029")) {
            try {
                String query = "SELECT test_name FROM diagnostic_test WHERE id = ? AND (is_active IS NULL OR is_active = true)";
                List<Map<String, Object>> result = jdbcTemplate.queryForList(query, key);
                if (!result.isEmpty()) {
                    Map<String, Object> row = result.get(0);
                    String testName = (String) row.get("test_name");
                    if (testName != null && !testName.isEmpty()) {
                        return testName;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to query diagnostic_test", e);
            }
        }

        return null;
    }

    public List<Map<String, Object>> getResources(String whereClause) {
        String query = "SELECT id::text as key, resource_type as resource_type_lkey, resource_key, " +
                "resource_name, is_allow_parallel, is_active as is_valid, null as facility_key " +
                "FROM resource WHERE " + (whereClause != null && !whereClause.isEmpty() ? whereClause : "1=1");
        
        return jdbcTemplate.queryForList(query);
    }

    public Map<String, Object> getResource(String key) {
        String query;
        if (key != null && key.matches("\\d+")) {
            query = "SELECT id::text as key, resource_type as resource_type_lkey, resource_key, " +
                    "resource_name, is_allow_parallel, is_active as is_valid, null as facility_key " +
                    "FROM resource WHERE id = " + key;
        } else {
            query = "SELECT id::text as key, resource_type as resource_type_lkey, resource_key, " +
                    "resource_name, is_allow_parallel, is_active as is_valid, null as facility_key " +
                    "FROM resource WHERE resource_key = '" + key + "'";
        }
        
        List<Map<String, Object>> result = jdbcTemplate.queryForList(query);
        return result.isEmpty() ? null : result.get(0);
    }

    public Long getResourceCount(String whereClause) {
        String query = "SELECT count(0) FROM resource WHERE " + (whereClause != null && !whereClause.isEmpty() ? whereClause : "1=1");
        return jdbcTemplate.queryForObject(query, Long.class);
    }
}

