package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ActivityPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityPlanRepository extends JpaRepository<ActivityPlan, Long> {

    Optional<ActivityPlan> findByCode(String code);

    boolean existsByCode(String code);

    List<ActivityPlan> findAllByOrderByViewOrderAscNameAsc();
}
