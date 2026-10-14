package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Analyzer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyzerRepository extends JpaRepository<Analyzer, Long> {
}
