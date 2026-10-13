package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChartProgressNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalChartProgressNoteRepository extends JpaRepository<DentalChartProgressNote, Long> {

    List<DentalChartProgressNote> findByChartIdAndDeletedAtIsNullOrderByCreatedDateDesc(Long chartId);
}
