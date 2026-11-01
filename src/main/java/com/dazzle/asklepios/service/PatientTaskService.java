package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.TaskScope;
import com.dazzle.asklepios.repository.PatientActivityRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientActivity.PatientTaskFilter;
import com.dazzle.asklepios.service.dto.patientActivity.PatientTaskSummaryVM;
import com.dazzle.asklepios.service.dto.patientActivity.PatientTaskVM;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Task lists across patients: My Tasks, Team Tasks and All Tasks.
 * A task is a patient activity with a due date; logged activities have none and stay out.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientTaskService {

    private final PatientActivityRepository patientActivityRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public Page<PatientTaskVM> search(PatientTaskFilter filter, Pageable pageable) {
        Page<PatientActivity> page = patientActivityRepository.findAll(
                inScope(filter.getScope()).and(matching(filter)),
                withDefaultSort(pageable)
        );

        List<Long> patientIds = page.getContent().stream().map(PatientActivity::getPatientId).distinct().toList();
        Map<Long, Patient> patients = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));

        return page.map(task -> {
            Patient patient = patients.get(task.getPatientId());
            return new PatientTaskVM(
                    task,
                    patient == null ? null : fullName(patient),
                    patient == null ? null : patient.getMedicalRecordNumber()
            );
        });
    }

    public PatientTaskSummaryVM summarize(TaskScope scope) {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfTomorrow = startOfToday.plus(1, ChronoUnit.DAYS);

        Specification<PatientActivity> open = inScope(scope).and(withStatus(PatientActivityStatus.OPEN));
        return new PatientTaskSummaryVM(
                patientActivityRepository.count(open),
                patientActivityRepository.count(open.and((root, query, cb) -> cb.and(
                        cb.greaterThanOrEqualTo(root.get("dueAt"), startOfToday),
                        cb.lessThan(root.get("dueAt"), startOfTomorrow)
                ))),
                patientActivityRepository.count(open.and((root, query, cb) -> cb.lessThan(root.get("dueAt"), now))),
                patientActivityRepository.count(open.and((root, query, cb) -> cb.isNull(root.get("assignedTo")))),
                patientActivityRepository.count(inScope(scope)
                        .and(withStatus(PatientActivityStatus.DONE))
                        .and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("completedAt"), startOfToday)))
        );
    }

    /**
     * Tasks only, narrowed to the user, their job role, or nobody in particular. Team and all
     * tasks stay in the facility the user is signed in to; tasks from before facilities were
     * recorded have none and show everywhere. My tasks follow the user across facilities.
     */
    private Specification<PatientActivity> inScope(TaskScope scope) {
        Long facilityId = SecurityUtils.getCurrentUserFacility().orElse(null);
        return (root, query, cb) -> {
            Predicate facilityTask = facilityId == null
                    ? cb.isNotNull(root.get("dueAt"))
                    : cb.and(
                            cb.isNotNull(root.get("dueAt")),
                            cb.or(cb.equal(root.get("facilityId"), facilityId), cb.isNull(root.get("facilityId")))
                    );
            return switch (scope == null ? TaskScope.MINE : scope) {
                case MINE -> cb.and(cb.isNotNull(root.get("dueAt")), cb.equal(root.get("assignedTo"), currentUsername()));
                case TEAM -> {
                    String role = currentJobRole();
                    if (role == null) {
                        yield cb.disjunction();
                    }
                    Expression<Boolean> inTeam = cb.function(
                            "jsonb_exists",
                            Boolean.class,
                            root.get("assignedRoles"),
                            cb.literal(role)
                    );
                    yield cb.and(facilityTask, cb.isTrue(inTeam));
                }
                case ALL -> facilityTask;
            };
        };
    }

    private Specification<PatientActivity> withStatus(PatientActivityStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private Specification<PatientActivity> matching(PatientTaskFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.getStatus() != null && !filter.getStatus().isEmpty()) {
                predicates.add(root.get("status").in(filter.getStatus()));
            }
            if (filter.getCategory() != null && !filter.getCategory().isBlank()) {
                predicates.add(cb.equal(root.get("category"), filter.getCategory()));
            }
            if (filter.getPriority() != null && !filter.getPriority().isEmpty()) {
                predicates.add(root.get("priority").in(filter.getPriority()));
            }
            if (filter.getPlanCode() != null && !filter.getPlanCode().isBlank()) {
                predicates.add(cb.equal(root.get("planCode"), filter.getPlanCode()));
            }
            if (filter.getAssignedTo() != null && !filter.getAssignedTo().isBlank()) {
                predicates.add(cb.equal(root.get("assignedTo"), filter.getAssignedTo()));
            }
            if (Boolean.TRUE.equals(filter.getUnassigned())) {
                predicates.add(cb.isNull(root.get("assignedTo")));
            }
            if (Boolean.TRUE.equals(filter.getOverdue())) {
                predicates.add(cb.equal(root.get("status"), PatientActivityStatus.OPEN));
                predicates.add(cb.lessThan(root.get("dueAt"), Instant.now()));
            }
            if (filter.getDueFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueAt"), filter.getDueFrom()));
            }
            if (filter.getDueTo() != null) {
                predicates.add(cb.lessThan(root.get("dueAt"), filter.getDueTo()));
            }
            String term = filter.getSearch() == null ? "" : filter.getSearch().trim().toLowerCase(Locale.ROOT);
            if (!term.isEmpty()) {
                String like = "%" + term + "%";
                Subquery<Long> patients = query.subquery(Long.class);
                Root<Patient> patient = patients.from(Patient.class);
                patients.select(patient.get("id")).where(cb.or(
                        likeIgnoringNull(cb, patient.get("firstName"), like),
                        likeIgnoringNull(cb, patient.get("lastName"), like),
                        likeIgnoringNull(cb, patient.get("medicalRecordNumber"), like)
                ));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("note")), like),
                        likeIgnoringNull(cb, root.get("outcome"), like),
                        cb.like(cb.lower(cb.function("replace", String.class,
                                root.get("activityType"), cb.literal("_"), cb.literal(" "))), like),
                        root.get("patientId").in(patients)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate likeIgnoringNull(CriteriaBuilder cb, Expression<String> path, String like) {
        return cb.like(cb.lower(cb.coalesce(path, "")), like);
    }

    private Pageable withDefaultSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "dueAt"));
    }

    private String fullName(Patient patient) {
        return Stream.of(patient.getFirstName(), patient.getSecondName(), patient.getThirdName(), patient.getLastName())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private String currentJobRole() {
        return userRepository.findByLogin(currentUsername())
                .map(user -> user.getJobRole() == null ? null : user.getJobRole().name())
                .orElse(null);
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin().orElse("unknown");
    }
}
