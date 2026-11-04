package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.TubeColorPrintCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TubeColorPrintCodeRepository extends JpaRepository<TubeColorPrintCode, Long> {
    Optional<TubeColorPrintCode> findByTubeColorKey(String tubeColorKey);
}
