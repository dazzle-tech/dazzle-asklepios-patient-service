package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ActivityTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityTemplateRepository extends JpaRepository<ActivityTemplate, Long> {

    Optional<ActivityTemplate> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCategoryCode(String categoryCode);

    List<ActivityTemplate> findAllByOrderByViewOrderAscNameAsc();
}
