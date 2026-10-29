package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.enumeration.JobRole;
import org.springframework.data.jpa.repository.JpaRepository;
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

}

