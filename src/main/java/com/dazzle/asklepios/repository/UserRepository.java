package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.enumeration.JobRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data R2DBC repository for the {@link User} entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLogin(String login);

    List<User> findByActivatedTrueAndJobRoleIn(Collection<JobRole> jobRoles);

    List<User> findByLoginIn(Collection<String> logins);

    /** Logins of active users with one of the job roles who work in a department of the facility. */
    @Query(value = "SELECT DISTINCT u.login FROM app_user u "
            + "JOIN user_departments ud ON ud.user_id = u.id "
            + "JOIN department d ON d.id = ud.department_id "
            + "WHERE u.activated = true AND d.facility_id = :facilityId AND u.job_role IN (:jobRoles)",
            nativeQuery = true)
    List<String> findActiveLoginsByFacilityAndJobRoles(
            @Param("facilityId") Long facilityId,
            @Param("jobRoles") Collection<String> jobRoles
    );

}

