package com.dazzle.asklepios.service.patientMerge;

import com.dazzle.asklepios.domain.enumeration.AppliesTo;
import com.dazzle.asklepios.domain.enumeration.MergeRuleType;
import com.dazzle.asklepios.domain.enumeration.RuleSeverity;
import com.dazzle.asklepios.domain.patientMerge.PatientMergeValidationRule;
import com.dazzle.asklepios.repository.PatientMergeValidationRuleRepository;
import com.dazzle.asklepios.service.dto.patientMerge.PatientMergeValidationRuleDTO;
import com.dazzle.asklepios.service.patientMerge.helpers.PatientMergeSupportHelper;
import com.dazzle.asklepios.web.rest.vm.patientMerge.PatientMergeValidationVM;
import com.dazzle.asklepios.web.rest.vm.patientMerge.ValidationIssueVM;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@Transactional
public class PatientMergeValidationRuleService {

    private final PatientMergeValidationRuleRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final PatientMergeSupportHelper supportHelper;

    public PatientMergeValidationRuleService(PatientMergeValidationRuleRepository repository, JdbcTemplate jdbcTemplate, PatientMergeSupportHelper supportHelper) {
        this.repository = repository;

        this.jdbcTemplate = jdbcTemplate;
        this.supportHelper = supportHelper;
    }


    public PatientMergeValidationRuleDTO save(PatientMergeValidationRuleDTO dto) {

        PatientMergeValidationRule entity = new PatientMergeValidationRule();

        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setRuleType(dto.ruleType());
        entity.setConditionConfig(dto.conditionConfig());
        entity.setAppliesTo(dto.appliesTo());
        entity.setSeverity(dto.severity());
        entity.setMessage(dto.message());
        entity.setActionRequired(dto.actionRequired());
        entity.setEnabled(dto.enabled());

        PatientMergeValidationRule saved = repository.save(entity);

        return new PatientMergeValidationRuleDTO(
                saved.getId(),
                saved.getCode(),
                saved.getName(),
                saved.getEntityName(),
                saved.getTableName(),
                saved.getRuleType(),
                saved.getConditionConfig(),
                saved.getAppliesTo(),
                saved.getSeverity(),
                saved.getMessage(),
                saved.getActionRequired(),
                saved.getEnabled()
        );
    }




        public PatientMergeValidationVM validate(Long fromPatientId, Long toPatientId) {

            List<PatientMergeValidationRule> rules =
                    repository.findByEnabledTrue();

            List<ValidationIssueVM> issues = new ArrayList<>();

            for (PatientMergeValidationRule rule : rules) {

                if (rule.getRuleType() != MergeRuleType.COUNT) {
                    continue;
                }

                boolean failed =
                        evaluateCountRule(rule, fromPatientId, toPatientId);

                if (failed) {
                    issues.add(
                            new ValidationIssueVM(
                                    rule.getCode(),
                                    rule.getSeverity(),
                                    rule.getMessage(),
                                    rule.getActionRequired()
                            )
                    );
                }
            }

            boolean valid = issues.stream()
                    .noneMatch(i -> i.severity() == RuleSeverity.ERROR);

            return new PatientMergeValidationVM(valid, issues);
        }

        // =========================================
        private boolean evaluateCountRule(
                PatientMergeValidationRule rule,
                Long fromPatientId,
                Long toPatientId
        ) {

            Map<String, Object> config = rule.getConditionConfig();

            String table = (String) config.get("table");
            String patientColumn = (String) config.get("patientColumn");

            List<String> matchColumns =
                    (List<String>) config.get("matchColumns");

            Map<String, Object> group =
                    (Map<String, Object>) config.get("group");

            List<Map<String, Object>> conditions =
                    (List<Map<String, Object>>) group.get("conditions");

            String operator = (String) config.get("operator");
            Number expected = (Number) config.get("value");

            supportHelper.validateIdentifier(table);
            supportHelper.validateIdentifier(patientColumn);

            StringBuilder sql = new StringBuilder();
            List<Object> params = new ArrayList<>();

            // ✅ SELECT
            if (matchColumns != null && !matchColumns.isEmpty()) {

                for (String col : matchColumns) {
                    supportHelper.validateIdentifier(col);
                }

                sql.append("SELECT ")
                        .append(String.join(", ", matchColumns))
                        .append(", COUNT(*) FROM ")
                        .append(table)
                        .append(" WHERE ");

            } else {

                sql.append("SELECT COUNT(*) FROM ")
                        .append(table)
                        .append(" WHERE ");
            }

            // ✅ appliesTo
            if (rule.getAppliesTo() == AppliesTo.BOTH) {
                sql.append(patientColumn).append(" IN (?, ?)");
                params.add(fromPatientId);
                params.add(toPatientId);
            } else if (rule.getAppliesTo() == AppliesTo.FROM_PATIENT) {
                sql.append(patientColumn).append(" = ?");
                params.add(fromPatientId);
            } else {
                sql.append(patientColumn).append(" = ?");
                params.add(toPatientId);
            }

            // ✅ conditions
            for (Map<String, Object> c : conditions) {

                String field = (String) c.get("field");
                String op = (String) c.get("operator");
                Object value = c.get("value");

                supportHelper.validateIdentifier(field);

                sql.append(" AND ")
                        .append(field)
                        .append(" ")
                        .append(op)
                        .append(" ?");

                params.add(value);
            }

            // ✅ GROUP BY CASE
            if (matchColumns != null && !matchColumns.isEmpty()) {

                sql.append(" GROUP BY ")
                        .append(String.join(", ", matchColumns));

                sql.append(" HAVING COUNT(*) ")
                        .append(operator)
                        .append(" ")
                        .append(expected);

                List<?> result = jdbcTemplate.queryForList(
                        sql.toString(),
                        params.toArray()
                );

                return !result.isEmpty();
            }

            // ✅ NORMAL COUNT
            Integer count = jdbcTemplate.queryForObject(
                    sql.toString(),
                    Integer.class,
                    params.toArray()
            );

            return compare(count != null ? count : 0, operator, expected.intValue());
        }

        private boolean compare(int actual, String operator, int expected) {

            return switch (operator) {
                case ">" -> actual > expected;
                case "<" -> actual < expected;
                case ">=" -> actual >= expected;
                case "<=" -> actual <= expected;
                case "=" -> actual == expected;
                case "!=" -> actual != expected;
                default -> false;
            };
        }
    }


