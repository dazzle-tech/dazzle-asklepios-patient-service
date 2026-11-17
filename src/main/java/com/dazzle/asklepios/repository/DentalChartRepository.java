package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface DentalChartRepository extends JpaRepository<DentalChart, Long>, JpaSpecificationExecutor<DentalChart>,
        DentalChartRepositoryCustom {

    List<DentalChart> findByPatient_Id(Long patientId);

    List<DentalChart> findAllByEncounter_IdOrderByIdAsc(Long encounterId);

    /** Row lock for clinical/inventory serialization. Ordinary {@code findById} calls do not lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DentalChart> findForUpdateById(Long id);

    List<DentalChart> findByPatient_IdAndIdNotOrderByCreatedDateDesc(Long patientId, Long excludeId);
}
