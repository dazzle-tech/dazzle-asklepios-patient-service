package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ActivityCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityCategoryRepository extends JpaRepository<ActivityCategory, Long> {

    Optional<ActivityCategory> findByCode(String code);

    boolean existsByCode(String code);

    List<ActivityCategory> findAllByOrderByViewOrderAscNameAsc();
}
