package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.MedFrequencySetup;
import com.dazzle.asklepios.domain.enumeration.MedFrequency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedFrequencySetupRepository extends JpaRepository<MedFrequencySetup, Long> {

    /** The setup used to split doses: an inactive setup counts as no setup */
    Optional<MedFrequencySetup> findByFrequencyAndIsActiveTrue(MedFrequency frequency);
}
