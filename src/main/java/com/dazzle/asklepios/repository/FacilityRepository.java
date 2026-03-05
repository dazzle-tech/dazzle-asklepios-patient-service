package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Facility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityRepository extends JpaRepository<Facility, Long> {
}
